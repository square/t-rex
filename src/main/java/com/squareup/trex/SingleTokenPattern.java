package com.squareup.trex;

import java.util.List;
import java.util.PrimitiveIterator;

/**
 * <p>
 *   An abstract class for matching just a single token.
 *   This is broken out from the {@link Pattern} class so that we can
 *   (1) provide a cleaner interface for single token matchers, and
 *   (2) type check single token matches against each other, for example
 *   for more efficient implementations of single token conjunction and
 *   disjunction operators. This is also a strict requirement to have for
 *   the negation operator.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
abstract class SingleTokenPattern extends Pattern {

  /**
   * <p>
   *   Returns true if this pattern matches the token at the given
   *   index of the input. This is called from {@link #consume(List, int, Matcher)}
   *   as a more convenient interface for single token matches.
   * </p>
   *
   * <p>
   *   If you call this method directly, you must remember to call
   *   {@link Pattern#registerMatch(int, int, Matcher)} on the called
   *   pattern if it indeed matches and you'd like to register the
   *   capture group. This is done for you in {@link #consume(List, int, Matcher)}.
   * </p>
   *
   * @param input The input we are matching.
   * @param index The index of the input we are matching.
   * @param context The matcher context for, e.g., timeouts.
   *
   * @return True if the input is matched at the argument index.
   */
  protected abstract boolean matches(
      List<? extends TRexInputToken> input,
      int index,
      Matcher<? extends TRexInputToken> context);

  /**
   * The length that we're matching. Generally speaking, this
   * should be 1, but in special cases (e.g., ^ or $), this is set
   * to 0 to allow for 0-length matches. It's unclear what happens
   * if you set this to any other value, and so I don't recommend
   * trying.
   *
   * @return The length that we're matching.
   */
  protected int length() {
    return 1;
  }

  /** {@inheritDoc} */
  @Override protected final PrimitiveIterator.OfInt consume(
      List<? extends TRexInputToken> input,
      int index,
      Matcher<? extends TRexInputToken> context) {
    if (matches(input, index, context)) {
      // Register the match with our matcher
      registerMatch(index, index + length(), context);
      // Return our dummy iterator
      return context.transientIterator(index + length());
    } else {
      return SingleValueIterator.EMPTY;
    }
  }

}
