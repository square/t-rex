package com.squareup.trex;

import java.util.BitSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.function.Consumer;

/**
 * An iterator over a disjunction of matches. This will return all
 * matches from the left hand side, followed by all matches on the right
 * hand side. This will iterate through all matches from the left hand side
 * before returning matches from the right hand side.
 */
class DisjunctionIterator implements PrimitiveIterator.OfInt {

  /**
   * A special value of {@link #nextInt} to specify that our iterator is not
   * primed yet.
   */
  private static final int NOT_PRIMED = -2;
  /**
   * A special value of {@link #nextInt} to specify that our iterator has
   * been exhausted.
   */
  private static final int NO_MORE_RESULTS = -1;

  /**
   * The left hand side of our disjunction.
   */
  private final PrimitiveIterator.OfInt lhs;
  /**
   * The right hand side of our disjunction.
   */
  private final PrimitiveIterator.OfInt rhs;
  /**
   * The index into our input sequence when we started matching this pattern.
   */
  private final int initialIndex;
  /**
   * The pattern this iterator was made from. Used for registering capture group
   * matches with the matcher.
   */
  private final DisjunctionPattern sourcePattern;
  /**
   * The matcher providing the context for our match. This is primarily used to define
   * our timeouts.
   */
  public final Matcher<? extends TRexInputToken> context;
  /**
   * We want to avoid returning the same match index multiple times. For example,
   * the pattern <pre>[{pos:NN}] | [{word:cat}]</pre>  should only return that it
   * matches once, even though both sides of the disjunction match. If the
   * length of our sequence is less than 64, the set of indices we have already
   * returned can fit into a primitive Long. This variable encodes that value.
   * If our length is longer than 64, we dynamically transfer the set encoded
   * by this long to {@link #alreadyReturnedSet} and proceed from there.
   *
   * See {@link #checkAndUpdateAlreadyReturned(int)}.
   */
  private long alreadyReturnedPrimitiveBitset = 0L;
  /**
   * This is the overflow object for {@link #alreadyReturnedPrimitiveBitset}. However,
   * we leave this as null by default to avoid to object allocation.
   *
   * See {@link #checkAndUpdateAlreadyReturned(int)}.
   */
  private BitSet alreadyReturnedSet = null;

  /**
   * The next index to return from {@link #nextInt()}. This is set in
   * {@link #prime()}, or else set to one of the special values
   * {@link #NOT_PRIMED} or {@link #NO_MORE_RESULTS}.
   */
  private int nextInt = NOT_PRIMED;

  /** Create a disjunction of two match iterators. */
  DisjunctionIterator(PrimitiveIterator.OfInt lhs, PrimitiveIterator.OfInt rhs,
      int initialIndex, DisjunctionPattern sourcePattern,
      Matcher<? extends TRexInputToken> context) {
    this.lhs = lhs;
    this.rhs = rhs;
    this.initialIndex = initialIndex;
    this.sourcePattern = sourcePattern;
    this.context = context;
  }

  /**
   * <p>
   *   This is an overly complicated Set lookup, made complicated to
   *   try to get a bit of efficiency, and more importantly to avoid an
   *   object allocation. The function will check if we've already returned
   *   a match at the given index, and if we haven't yet then mark that we've
   *   returned a match at the given index.
   * </p>
   *
   * <p>
   *   Implementation-wise, this function will try to use the primitive long
   *   {@link #alreadyReturnedPrimitiveBitset} to check if we've returned the
   *   index. If we've overflowed the primitive long, it'll transfer all of the
   *   values into {@link #alreadyReturnedSet} and use that for future checks.
   * </p>
   *
   * @param index The index we are looking up in the set.
   *
   * @return True if we've already returned a match at this index. We also set the
   *         value of |index| to true in the set.
   */
  boolean checkAndUpdateAlreadyReturned(int index) {
    if (index >= 64 || alreadyReturnedSet != null) {
      // Case: we've exhausted our primitive bitset
      if (alreadyReturnedSet == null) {
        // if we need to copy over to the large bitset, do it now
        alreadyReturnedSet = new BitSet();
        for (int i = 0; i < 64; ++i) {
          if (((alreadyReturnedPrimitiveBitset >>> i) & 0x1) == 1) {
            alreadyReturnedSet.set(i);
          }
        }
      }
      // Check if we've already returned this index
      if (alreadyReturnedSet.get(index)) {
        return true;
      } else {
        alreadyReturnedSet.set(index);
        return false;
      }
    } else {
      // Case: we still fit into our small bitset
      if (((alreadyReturnedPrimitiveBitset >>> index) & 0x1) == 1) {
        return true;
      } else {
        alreadyReturnedPrimitiveBitset |= 1L << index;
        return false;
      }
    }
  }

  /**
   * Prime our iterator. After this function, {@link #nextInt} will be either
   * a valid index, or {@link #NO_MORE_RESULTS}.
   */
  private void prime() {
    if (nextInt >= 0) {
      return;
    }
    int next;
    if (lhs.hasNext()) {
      next = lhs.nextInt();
    } else if (rhs.hasNext()) {
      next = rhs.nextInt();
    } else {
      nextInt = NO_MORE_RESULTS;
      return;
    }
    if (checkAndUpdateAlreadyReturned(next)) {
      prime();
    } else {
      this.nextInt = next;
    }
  }

  /** {@inheritDoc} */
  @Override public boolean hasNext() {
    prime();
    return nextInt >= 0;
  }

  /** {@inheritDoc} */
  @Override public int nextInt() {
    if (nextInt < 0) {
      throw new NoSuchElementException();
    } else {
      // Register our match
      sourcePattern.registerMatch(initialIndex, nextInt, context);
      // Return
      int rtn = nextInt;
      nextInt = NOT_PRIMED;
      return rtn;
    }
  }
}

/**
 * <p>
 *   A pattern encoding the disjunction of two patterns. This will match if either
 *   of the two patterns matches, though it'll only return matches of a given length
 *   once (i.e., if both patterns match, it'll return the match only once).
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class DisjunctionPattern extends Pattern {

  /**
   * The left hand side to match.
   */
  public final Pattern lhs;
  /**
   * The right hand side to match.
   */
  public final Pattern rhs;

  /** Create a disjunction of two patterns */
  public DisjunctionPattern(Pattern lhs, Pattern rhs) {
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
    // Get the iterator of matches on the left hand side
    PrimitiveIterator.OfInt lhsIter = lhs.consume(input, index, context);
    // Since tryConsume is allowed to return a transient iterator, we
    // create a new object to store this iterator, since we'll need to
    // concat it with the right hand side. This is an unfortunate object
    // creation that can be avoided with more complex code, but Gabor doesn't
    // believe the runtime savings warrants the complexity introduced, at least
    // not without measuring first.
    if (lhsIter instanceof SingleValueIterator) {

      lhsIter = lhsIter.hasNext()
          ? new SingleValueIterator(lhsIter.next())
          : SingleValueIterator.EMPTY;
    }
    // Get the iterator of matches on the right hand side
    PrimitiveIterator.OfInt rhsIter = rhs.consume(input, index, context);
    // As above, we need to make the iterator non-transient
    if (rhsIter instanceof SingleValueIterator) {

      rhsIter = rhsIter.hasNext()
          ? new SingleValueIterator(rhsIter.next())
          : SingleValueIterator.EMPTY;
    }
    // Return our joint iterator
    return new DisjunctionIterator(lhsIter, rhsIter, index, this, context);
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
        .append(" | ")
        .append(rhs.toString());
  }
}

