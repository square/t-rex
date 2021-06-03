package com.squareup.trex;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Stack;
import java.util.function.Consumer;

/**
 * When backtracking, we need to save the state for a previous valid
 * match on a branch that we're continuing to explore.
 * This class encapsulates this saved state.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class SequencePatternBranchState {
  /**
   * The index we'd matched up so far. This is where we should
   * continue running the iterator from.
   */
  final int index;
  /**
   * The iterator of matches we have, starting from {@link #index}
   * and having already matched {@link #matchCount} components.
   */
  final PrimitiveIterator.OfInt matches;
  /**
   * The number of times we've matched our components so far.
   */
  final int matchCount;

  /**
   * Create a saved branch state.
   *
   * @param index @see #index
   * @param matches @see #matches
   * @param matchCount @see #matchCount
   */
  SequencePatternBranchState(
      int index,
      PrimitiveIterator.OfInt matches,
      int matchCount) {
    this.index = index;
    this.matches = matches;
    this.matchCount = matchCount;
  }
}

/**
 * The implementation of the iterator for matching an arbitrary
 * sequence of components. The meat of the work for a
 * {@link SequencePattern} is done in this class.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class SequencePatternIterator implements PrimitiveIterator.OfInt {
  /**
   * The state of our iterator at the moment. This is stored in the variable
   * {@link #state} and updated as we run the various iterator functions.
   */
  enum State {
    NEVER_PRIMED,
    HAVE_MATCH,
    BRANCH_EXHAUSTED,
    ITERATOR_EXHAUSTED
  }

  /**
   * We are a sequence of either a single component repeated or a list of components.
   * If it's a single component repeated, then this stores that component. Otherwise,
   * the list of components is stored in {@link #components}. Exactly one of these two
   * fields must be non-null.
   */
  /* @Nullable */ private final Pattern component;

  /**
   * We are a sequence of either a single component repeated or a list of components.
   * If it's a single component repeated, the component is stored in {@link #component}.
   * Otherwise, this field stores the list of components we need to match. Exactly one
   * of these two fields must be non-null.
   */
  /* @Nullable */ private final List<Pattern> components;

  /**
   * The minimum number of components we need to match, inclusive of this number.
   * This is either the minimum repeat count, or 0 if we have a {@linkplain #components
   * components list}.
   */
  public final int minCountInclusive;

  /**
   * The maximum number of components we need to match, inclusive of this number.
   * This is either the maximum repeat count, or the length of our {@linkplain #components
   * components list}.
   */
  public final int maxCountInclusive;

  /**
   * The input we are matching with this iterator.
   */
  public final List<? extends TRexInputToken> input;

  /**
   * The matcher providing the context for our match. This is primarily used to define
   * our timeouts.
   */
  public final Matcher<? extends TRexInputToken> context;

  /**
   * If true, iterate over matches as a reluctant quantifier. That is,
   * return shorter matches before longer ones. This is the case for patterns
   * like 'token*?'.
   */
  public final boolean isReluctant;

  /**
   * The stack for our backtracking search. Each element of the stack defines a decision
   * point where we had a potentially valid partial match (or completely valid exact match)
   * and chose one path instead of the other. This is, effectively, a mildly optimized version
   * of a vanilla depth-first-search stack.
   */
  /* @Nullable */
  private Stack<SequencePatternBranchState> branchStack = null;  // avoid needless object allocation

  /**
   * The index into our input sequence when we started matching this pattern.
   */
  public final int initialIndex;

  /**
   * The pattern this iterator was made from. Used for registering capture group
   * matches with the matcher.
   */
  public final SequencePattern sourcePattern;

  /**
   * The next index to return from the iterator. This is only a valid value if the state
   * is in {@link State#HAVE_MATCH}. Otherwise, this is a stale index and should not be
   * returned.
   */
  private int nextIndex;

  /**
   * The current state of the iterator. This is updated directly in the various methods
   * in this class, and each method enforces some invariant on what the state will be after
   * the method returns.
   */
  private State state = State.NEVER_PRIMED;

  /**
   * A variable used for a reluctant quantifier to store the current index for the
   * backtracking search. This can be thought of as the {@link SequencePatternBranchState#index}
   * field of a dummy next state.
   */
  private int reluctantIndex;

  /**
   * A variable used for a reluctant quantifier to store the current match count for the
   * backtracking search. This can be thought of as the
   * {@link SequencePatternBranchState#matchCount} field of a dummy next state.
   */
  private int reluctantMatchCount;

  /** A straightforward constructor for the fields of the iterator */
  SequencePatternIterator(
      /* @Nullable */ Pattern component,
      /* @Nullable */ List<Pattern> components,
      int minCountInclusive, int maxCountInclusive,
      boolean isReluctant,
      List<? extends TRexInputToken> input, int initialIndex,
      SequencePattern sourcePattern,
      Matcher<? extends TRexInputToken> context) {
    assert (component == null || components == null) && !(component == null && components == null)
        : "Must have exactly one component type";
    this.component = component;
    this.components = components;
    this.minCountInclusive = minCountInclusive;
    this.maxCountInclusive = maxCountInclusive;
    this.isReluctant = isReluctant;
    this.input = input;
    this.initialIndex = initialIndex;
    this.nextIndex = initialIndex;
    this.sourcePattern = sourcePattern;
    this.context = context;
    this.reluctantIndex = initialIndex;
    this.reluctantMatchCount = 0;
  }

  /**
   * <p>
   *   Run all the way down a given branch of our backtracking search, attempting
   *   to create the greediest match on that branch. This stores all of the branches
   *   it did not take in {@link #branchStack} so that we can return to them later,
   *   but is not itself responsible for running the backtracking. It simply reports
   *   whether or not this branch was a successful match.
   * </p>
   *
   * <p>
   *   This function is guaranteed to leave us in a state of either
   *   {@link State#HAVE_MATCH} or {@link State#BRANCH_EXHAUSTED}.
   *   The former if we have a match on this branch of the search,
   *   and the latter if we do not have a match, because we matched
   *   too few or too many words.
   * </p>
   *
   * @param index The start index from which we are starting our match.
   *              This is an index into the tokens list we are matching.
   * @param initialComponentsConsumed The number of components we have already consumed.
   *                                  This is an index into our components list, or the
   *                                  number of times we've repeated our component.
   */
  private void primeBranchEager(int index, int initialComponentsConsumed) {
    // Checking for timeouts is actually a moderately expensive operation,
    // on the order of a few dozen ns per invocation. Putting the check here
    // was chosen as a tradeoff between checking frequently enough to be useful,
    // but not so frequently as to incur a noticeable slowdown. This function is
    // chosen as a compromise between being called frequently enough to be useful,
    // but not so frequently that we slow down execution too much.
    if (this.context.timeoutExceeded()) {
      throw new RuntimeException("Timout exceeded for pattern match");
    }

    int numComponentsConsumed;
    for (numComponentsConsumed = initialComponentsConsumed;
        // note: '<= input.size()' to allow for terminal 0-length matches
        numComponentsConsumed < maxCountInclusive && index <= input.size();
        ++numComponentsConsumed) {
      PrimitiveIterator.OfInt match
          = (components == null ? component : components.get(numComponentsConsumed))
              .consume(input, index, context);
      if (match.hasNext()) {
        // We have a match at this depth
        int indexBeforeMatch = index;
        index = match.nextInt();
        if (!(match instanceof SingleValueIterator)) {
          // We may have more matches. Push the iterator to the stack
          if (branchStack == null) { branchStack = new Stack<>(); }
          branchStack.push(new SequencePatternBranchState(indexBeforeMatch,
              match, numComponentsConsumed));
        } else if (numComponentsConsumed >= minCountInclusive) {
          // Save this state as a valid state we can come back to,
          // if we want to match fewer than our max number of matches
          if (branchStack == null) { branchStack = new Stack<>(); }
          branchStack.push(new SequencePatternBranchState(indexBeforeMatch,
              SingleValueIterator.EMPTY, numComponentsConsumed));
        }
        // Update the index if appropriate
        // Note the '+ 1', since we just matched a token not yet
        // represented in |matchCount|
        if (numComponentsConsumed + 1 >= minCountInclusive) {
          // Set our next index to the most greedy match so far
          this.nextIndex = index;
        }
      } else {
        break;
      }
    }
    // If we didn't match enough tokens, or matched too many,
    // we don't have a match
    if (numComponentsConsumed < this.minCountInclusive ||
        numComponentsConsumed > this.maxCountInclusive) {
      state = State.BRANCH_EXHAUSTED;
    } else {
      state = State.HAVE_MATCH;
    }
  }

  /**
   * <p>
   *   Run our backtracking search to find the next match to return.
   * </p>
   *
   * <p>
   *   This will always leave our state in one of {@link State#HAVE_MATCH}
   *   or {@link State#ITERATOR_EXHAUSTED}
   * </p>
   *
   * @see #primeReluctant(), the reluctant variant of the backtracking search.
   */
  private void primeEager() {
    // If we've never been primed, prime our depth stack
    if (state == State.NEVER_PRIMED) {
      primeBranchEager(this.nextIndex, 0);
    }
    assert state != State.NEVER_PRIMED : "We should have primed ourselves axiomatically by now";

    // Run our backtracking search until we find a match
    while (state == State.BRANCH_EXHAUSTED &&  // only run while we don't have a match
           branchStack != null &&  // if we never had more options, stop searching
           !branchStack.isEmpty()  // if we're out of options, stop searching
    ) {
      SequencePatternBranchState branchState = this.branchStack.pop();
      // Get the match
      if (branchState.matches.hasNext()) {
        // There are more matches we can make at this match count.
        // Consume the next element from the iterator
        this.nextIndex = branchState.matches.nextInt();
        assert this.nextIndex >= 0 : "Our iterator should never return an invalid match value";
        // Push ourselves back on the stack, if this remains a valid state.
        // This remains a valid state if either:
        //   1. There are more matches in the iterator
        //   2. Not matching is a valid option at this state
        if (!(branchState.matches instanceof SingleValueIterator)) {
          this.branchStack.push(branchState);
        }
        // Re-prime the stack, if we're valid so far
        primeBranchEager(this.nextIndex, branchState.matchCount + 1);
      } else if (branchState.matchCount >= minCountInclusive) {
        // We should only get here if we're allowed to simply not match this element
        // That is, this is the only situation when empty iterators are pushed back
        // on th stack (see case 2 above)
        this.nextIndex = branchState.index;
        state = State.HAVE_MATCH;
      }  // otherwise, continue searching
    }

    // Promote complete branch exhaustion to iterator exhaustion.
    // If our last branch failed to match, it means the whole
    // iterator has failed to match as well.
    if (state == State.BRANCH_EXHAUSTED) {
      state = State.ITERATOR_EXHAUSTED;
    }
  }

  /**
   * <p>
   *   Run our backtracking search to find the next match to return. This runs the search
   *   as a reluctant search, returning shorter matches before longer ones.
   *   This function is also responsible for checking on the timeouts, as the backtracking
   *   search is likely the slow part of any match.
   * </p>
   *
   * <p>
   *   This will always leave our state in one of {@link State#HAVE_MATCH}
   *   or {@link State#ITERATOR_EXHAUSTED}
   * </p>
   *
   * @see #primeEager(), the eager variant of the backtracking search.
   */
  @SuppressWarnings("fallthrough")
  private void primeReluctant() {
    // Check for timeouts
    if (this.context.timeoutExceeded()) {
      throw new RuntimeException("Timout exceeded for pattern match");
    }

    switch (state) {
      case NEVER_PRIMED:
        if (minCountInclusive == 0) {
          // Special case: we're allowed to match nothing.
          // note that nextIndex is already set to the correct value.
          state = State.HAVE_MATCH;
          break;
        }
        state = State.BRANCH_EXHAUSTED;
        // fall through

      case BRANCH_EXHAUSTED:  // State when we need to re-prime
        int index = reluctantIndex;
        int numComponentsConsumed = reluctantMatchCount;
        // Base case is either we've consumed too much or we're out of tokens
        // This is the inverse condition in primeBranchEager for continuing the loop
        // If it triggers, it means we have nothing left to consume.
        // note: '<= input.size()' to allow for terminal 0-length matches
        // This branch is taken if the base case is not met
        if (numComponentsConsumed < maxCountInclusive && index <= input.size()) {
          // Recursive case: try to match
          PrimitiveIterator.OfInt match
              = (components == null ? component : components.get(numComponentsConsumed))
              .consume(input, index, context);
          if (match.hasNext()) {
            // We have a match at this depth
            reluctantIndex = match.nextInt();
            reluctantMatchCount += 1;
            // Save our state for backtracking, since we have more options to match here
            if (!(match instanceof SingleValueIterator)) {
              if (branchStack == null) {
                branchStack = new Stack<>();
              }
              branchStack.push(new SequencePatternBranchState(index,
                  match, numComponentsConsumed));
            }
            // Check if we have a match
            if (reluctantMatchCount >= minCountInclusive) {
              // We've matched enough times; mark ourselves as matching
              this.state = State.HAVE_MATCH;
              this.nextIndex = reluctantIndex;
            } else {
              // We haven't matched enough times; recurse
              // Only recurse if we've either (1) made forward progress, or
              // (2) have a different token to try next.
              if (reluctantIndex > index || components != null) {
                primeReluctant();
              }
            }
          }
        }
        break;
      default:
      case HAVE_MATCH:
      case ITERATOR_EXHAUSTED:
        break;
    }

    // Check if we can backtrack
    if (state == State.BRANCH_EXHAUSTED &&
        branchStack != null &&
        !branchStack.isEmpty()) {
      // We have a saved state we can continue from.
      // Restore ourselves to the saved state
      SequencePatternBranchState savedState = branchStack.pop();
      if (savedState.matches.hasNext()) {
        reluctantIndex = savedState.matches.nextInt();
        reluctantMatchCount = savedState.matchCount + 1;
        if (savedState.matches.hasNext()) {
          branchStack.push(savedState);
        }
        // Check if this is an immediate success
        if (reluctantMatchCount >= minCountInclusive) {
          this.state = State.HAVE_MATCH;
          this.nextIndex = reluctantIndex;
        } else {
          // If it's not an immediate success, continue
          primeReluctant();
        }
      } else {
        // Skip over empty iterators while backtracking
        primeReluctant();
      }
    } else if (state == State.BRANCH_EXHAUSTED) {
      // We're out of options
      state = State.ITERATOR_EXHAUSTED;
    }
  }

  /** {@inheritDoc} */
  @Override public boolean hasNext() {
    switch (state) {
      case HAVE_MATCH:
        return true;
      case ITERATOR_EXHAUSTED:
        return false;
      default:
        if (isReluctant) {
          primeReluctant();
        } else {
          primeEager();
        }
        assert state == State.HAVE_MATCH || state == State.ITERATOR_EXHAUSTED
            : "By the invariant of the prime() method, we should be in one of these states";
        // Note: by the invariant assertion above, we cannot infinite loop on this method
        return hasNext();
    }
  }

  /** {@inheritDoc} */
  @Override public int nextInt() {
    if (!hasNext()) {  // note[gabor]: hasNext() primes the iterator if needed
      throw new NoSuchElementException();
    }
    // Reset our priming state
    state = State.BRANCH_EXHAUSTED;
    // Register the match
    sourcePattern.registerMatch(initialIndex, nextIndex, context);
    // Return our next index
    return this.nextIndex;
  }
}

/**
 * <p>
 *   A pattern that's composed of a sequence of other patterns. This is a unified
 *   implementation of two cases of this: (1) where the sequence is a sequence of different
 *   patterns (e.g., 'foo bar'), and (2) where this sequence is the same pattern repeated
 *   a certain number of times (e.g., 'foo+').
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class SequencePattern extends Pattern {

  /**
   * We are a sequence of either a single component repeated or a list of components.
   * If it's a single component repeated, then this stores that component. Otherwise,
   * the list of components is stored in {@link #components}. Exactly one of these two
   * fields must be non-null.
   */
  /* @Nullable */ private final Pattern component;

  /**
   * We are a sequence of either a single component repeated or a list of components.
   * If it's a single component repeated, the component is stored in {@link #component}.
   * Otherwise, this field stores the list of components we need to match. Exactly one
   * of these two fields must be non-null.
   */
  /* @Nullable */ private final List<Pattern> components;

  /**
   * The minimum number of components we need to match, inclusive of this number.
   * This is either the minimum repeat count, or 0 if we have a {@linkplain #components
   * components list}.
   */
  public final int minCountInclusive;

  /**
   * The maximum number of components we need to match, inclusive of this number.
   * This is either the maximum repeat count, or the length of our {@linkplain #components
   * components list}.
   * This is set to {@link Integer#MAX_VALUE} if no upper bound is set (e.g., for the * operator).
   */
  public final int maxCountInclusive;

  /**
   * If true, we try to return matches reluctantly -- that is, return the shortest
   * possible match first. By default, this is false and we match eagerly --
   * returning the longest match first.
   */
  public final boolean isReluctant;

  /**
   * Create a new sequence pattern.
   *
   * @param component See {@link #component}. Exactly one of this or |components| must be defined.
   * @param components See {@link #components}. Exactly one of this or |components| must be defined.
   * @param minCountInclusive See {@link #minCountInclusive}.
   * @param maxCountInclusive See {@link #maxCountInclusive}.
   * @param isReluctant See {@link #isReluctant}.
   */
  SequencePattern(
      /* @Nullable */ Pattern component,
      /* @Nullable */ List<Pattern> components,
      int minCountInclusive,
      int maxCountInclusive,
      boolean isReluctant) {
    assert (component == null || components == null) && !(component == null && components == null)
        : "Must have exactly one component type";
    this.component = component;
    this.components = components;
    this.minCountInclusive = minCountInclusive;
    this.maxCountInclusive = maxCountInclusive;
    this.isReluctant = isReluctant;
  }

  /** {@inheritDoc} */
  @Override
  protected PrimitiveIterator.OfInt consume(List<? extends TRexInputToken> input, int index,
      Matcher<? extends TRexInputToken> context) {
    if (components == null && component == null) {
      return SingleValueIterator.EMPTY;
    }
    return new SequencePatternIterator(component, components, minCountInclusive, maxCountInclusive,
        isReluctant, input, index, this, context);
  }

  /** {@inheritDoc} */
  @Override protected void forEachComponent(Consumer<Pattern> fn) {
    if (this.component != null) {
      this.component.forEachComponent(fn);
    }
    if (this.components != null) {
      for (Pattern component : this.components) {
        component.forEachComponent(fn);
      }
    }
    fn.accept(this);
  }

  /** {@inheritDoc} */
  @Override protected void populateToString(StringBuilder b) {
    if (components != null) {
      // This is a list of components
      for (int i = 0; i < components.size(); ++i) {
        if (i != 0) {
          b.append(' ');
        }
        b.append(components.get(i).toString());
      }
    } else if (component == null) {
      // This case should be impossible
      b.append("???");
    } else {
      // This is a repeated component
      if (minCountInclusive == 0 && maxCountInclusive == Integer.MAX_VALUE) {
        b.append(component.toString()).append('*');
      } else if (minCountInclusive == 1 && maxCountInclusive == Integer.MAX_VALUE) {
        b.append(component.toString()).append('+');
      } else if (minCountInclusive == 0 && maxCountInclusive == 1) {
        b.append(component.toString()).append('?');
      } else if (maxCountInclusive == Integer.MAX_VALUE) {
        b.append(component.toString())
            .append('{')
            .append(minCountInclusive)
            .append(',')
            .append('}');
      } else if (minCountInclusive == maxCountInclusive) {
        b.append(component.toString())
            .append('{')
            .append(minCountInclusive)
            .append('}');
      } else {
        b.append(component.toString())
            .append('{')
            .append(minCountInclusive)
            .append(',')
            .append(maxCountInclusive)
            .append('}');
      }
      // Add the '?' to signify we're reluctant, if relevant
      if (isReluctant) {
        b.append('?');
      }
    }
  }

  /**
   * A convenience function for implementing the star operator (e.g., foo*).
   */
  static SequencePattern star(Pattern match, boolean isReluctant) {
    return new SequencePattern(match, null, 0, Integer.MAX_VALUE,
        isReluctant);
  }

  /**
   * A convenience function for implementing the plus operator (e.g., foo+).
   */
  static SequencePattern plus(Pattern match, boolean isReluctant) {
    return new SequencePattern(match, null, 1, Integer.MAX_VALUE,
        isReluctant);
  }

  /**
   * A convenience function for implementing the question mark operator (e.g., foo?).
   */
  static SequencePattern qmark(Pattern match, boolean isReluctant) {
    return new SequencePattern(match, null, 0, 1,
        isReluctant);
  }

  /**
   * A convenience function for matching a sequence of patterns (e.g., 'foo bar').
   */
  static SequencePattern sequence(List<Pattern> seq) {
    return new SequencePattern(null, seq, seq.size(), seq.size(), false /* is reluctant */);
  }
}
