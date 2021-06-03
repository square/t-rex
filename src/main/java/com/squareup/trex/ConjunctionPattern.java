package com.squareup.trex;

import java.util.BitSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.function.Consumer;

/**
 * <p>
 *   An iterator for returning conjunction matches, created by the
 *   {@link ConjunctionPattern} class. This will try to find any indices
 *   where the left hand side and right hand side of a conjunction both match
 *   to the same index.
 * </p>
 *
 * <p>
 *   Note that for single-token conjunctions, {@link SingleTokenConjunctionPattern}
 *   is substantially more efficient, and should be used instead.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class ConjunctionIterator implements PrimitiveIterator.OfInt {

  // Note[gabor]:
  // In principle, we could pull the same trick for lhsMatches and rhsMatches as in
  // {@link DisjunctionIterator#checkAndUpdateAlreadyReturned(int)} and avoid the
  // object creation here, but with conjunctions substantially more rare than
  // disjunctions the effort+complexity was not deemed worthwhile.
  /** The set of indices that matched the left hand side of the conjunction. */
  private final BitSet lhsMatches = new BitSet();
  /** The set of indices that matched the right hand side of the conjunction. */
  private final BitSet rhsMatches = new BitSet();
  /**
   * The smallest index that matched the left hand side, so far.
   * This should always be argmin({@link #lhsMatches})
   */
  private int minLhsMatch = Integer.MAX_VALUE;
  /**
   * The smallest index that matched the right hand side, so far.
   * This should always be argmin({@link #rhsMatches})
   */
  private int minRhsMatch = Integer.MAX_VALUE;
  /**
   * The set of matches we can still pull from the left hand side.
   */
  private final PrimitiveIterator.OfInt lhsIter;
  /**
   * The set of matches we can still pull from the right hand side.
   */
  private final PrimitiveIterator.OfInt rhsIter;
  /**
   * If true, we have a next element.
   * {@link #next} should be a valid match index if this is true.
   */
  private boolean primed = false;
  /**
   * The next element to return, if we are {@linkplain #primed primed}.
   */
  private int next;
  /**
   * The index into our input sequence when we started matching this pattern.
   */
  private final int initialIndex;
  /**
   * The pattern this iterator was made from. Used for registering capture group
   * matches with the matcher.
   */
  private final ConjunctionPattern sourcePattern;
  /**
   * The matcher providing the context for our match. This is primarily used to define
   * our timeouts.
   */
  public final Matcher<? extends TRexInputToken> context;

  /** The straightforward constructor */
  ConjunctionIterator(int lhsNext, int rhsNext,
      PrimitiveIterator.OfInt lhsIter, PrimitiveIterator.OfInt rhsIter,
      int initialIndex, ConjunctionPattern sourcePattern,
      Matcher<? extends TRexInputToken> context) {
    this.lhsMatches.set(lhsNext);
    this.rhsMatches.set(rhsNext);
    this.lhsIter = lhsIter;
    this.rhsIter = rhsIter;
    if(lhsNext >= 0 && lhsNext == rhsNext) {
      primed = true;
      next = lhsNext;
    }
    this.initialIndex = initialIndex;
    this.sourcePattern = sourcePattern;
    this.context = context;
  }

  /**
   * Find the next possible iterator match
   */
  private void prime() {
    while (lhsIter.hasNext() || rhsIter.hasNext()) {
      // Add another element to the set of things being considered
      int match;
      if (lhsIter.hasNext() && (minLhsMatch > minRhsMatch || !rhsIter.hasNext())) {
        match = lhsIter.next();
        minLhsMatch = Math.min(minLhsMatch, match);
        lhsMatches.set(match);
      } else {
        match = rhsIter.next();
        minRhsMatch = Math.min(minRhsMatch, match);
        rhsMatches.set(match);
      }
      // Check if there's a new match
      if (lhsMatches.get(match) && rhsMatches.get(match)) {
        primed = true;
        next = match;
        return;
      }
    }
  }

  /** {@inheritDoc} */
  @Override public boolean hasNext() {
    if (!primed) {
      prime();
    }
    return primed;
  }

  /** {@inheritDoc} */
  @Override public int nextInt() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    // Register our match
    sourcePattern.registerMatch(initialIndex, next, context);
    // Return
    primed = false;
    return next;
  }
}

/**
 * A pattern that matches the conjunction of two patterns. This is considered true
 * when, at the given index, both patterns match and both patterns match the same number
 * of tokens.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class ConjunctionPattern extends Pattern {

  /**
   * The left hand side of the conjunction.
   */
  public final Pattern lhs;
  /**
   * The right hand side of the conjunction.
   */
  public final Pattern rhs;

  /**
   * Create a new conjunction pattern.
   *
   * @param lhs See {@link #lhs}
   * @param rhs See {@link #rhs}
   */
  public ConjunctionPattern(Pattern lhs, Pattern rhs) {
    this.lhs = lhs;
    this.rhs = rhs;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   *   This function is considered successful if both sides of the expression
   *   match with the same length.
   * </p>
   * @return
   */
  @Override protected PrimitiveIterator.OfInt consume(
      List<? extends TRexInputToken> input,
      int index,
      Matcher<? extends TRexInputToken> context) {
    // Check the left hand side
    PrimitiveIterator.OfInt lhsIter = lhs.consume(input, index, context);
    int lhsFirst;
    if (!lhsIter.hasNext()) {
      // short circuit: we failed the LHS
      return SingleValueIterator.EMPTY;
    } else {
      lhsFirst = lhsIter.next();
    }
    if (!lhsIter.hasNext()) {
      lhsIter = SingleValueIterator.EMPTY;  // freeze if transient iterator
    }

    // Check the right hand side
    PrimitiveIterator.OfInt rhsIter = rhs.consume(input, index, context);
    int rhsFirst;
    if (!rhsIter.hasNext()) {
      // Short circuit: we failed the RHS
      return SingleValueIterator.EMPTY;
    } else {
      rhsFirst = rhsIter.next();
    }
    if (!rhsIter.hasNext()) {
      rhsIter = SingleValueIterator.EMPTY;  // freeze if transient iterator
    }

    if (!lhsIter.hasNext() && !rhsIter.hasNext()) {
      // Special case for 2 single-value iterators
      if (lhsFirst == rhsFirst) {
        // We have a single element exact match. This is our most common
        // match case, since we're usually doing conjunctions over just
        // as single token
        return context.transientIterator(lhsFirst);
      } else {
        // We only have one match on either side, and they didn't
        // match the same length. This is therefore not a match.
        return SingleValueIterator.EMPTY;
      }
    } else {
      // We have to construct our iterator
      return new ConjunctionIterator(lhsFirst, rhsFirst, lhsIter, rhsIter,
          index, this, context);
    }
  }

  /** {@inheritDoc} */
  @Override protected void forEachComponent(Consumer<Pattern> fn) {
    lhs.forEachComponent(fn);
    rhs.forEachComponent(fn);
    fn.accept(this);
  }

  /** {@inheritDoc} */
  @Override protected void populateToString(StringBuilder b) {
    b.append(lhs.toString())
        .append(" & ")
        .append(rhs.toString());
  }
}
