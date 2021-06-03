package com.squareup.trex;

import java.util.List;
import java.util.function.Consumer;

/**
 * A special-case pattern for a conjunction in a single token. This is much
 * simpler (+faster) than the multi-token equivalent {@link ConjunctionPattern}.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class SingleTokenConjunctionPattern extends SingleTokenPattern {

  /**
   * The left hand side of the conjunction.
   */
  public final SingleTokenPattern lhs;
  /**
   * The right hand side of the conjunction.
   */
  public final SingleTokenPattern rhs;
  /**
   * The length of this conjunction match. This should be either 0
   * or 1, depending on whether this pattern actually consumes
   * a token or not.
   */
  private final int length;

  /**
   * Create a new single token conjunction pattern.
   *
   * @param lhs See {@link #lhs}
   * @param rhs See {@link #rhs}
   */
  SingleTokenConjunctionPattern(SingleTokenPattern lhs,
      SingleTokenPattern rhs) {
    this.lhs = lhs;
    this.rhs = rhs;
    this.length = lhs.length();
    assert length == rhs.length()
        : "We cannot have a single token conjunction pattern if lengths mismatch";
  }

  /** {@inheritDoc} */
  @Override protected int length() {
    return length;
  }

  /** {@inheritDoc} */
  @Override protected boolean matches(List<? extends TRexInputToken> input, int index,
      Matcher<? extends TRexInputToken> context) {
    if (lhs.matches(input, index, context)) {
      // Register the match on the left hand side
      lhs.registerMatch(index, index + length(), context);
      if (rhs.matches(input, index, context)) {
        // Register the match on the right hand side
        rhs.registerMatch(index, index + length(), context);
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override protected void forEachComponent(Consumer<Pattern> fn) {
    lhs.forEachComponent(fn);
    rhs.forEachComponent(fn);
    fn.accept(this);
  }

  /** {@inheritDoc} */
  @Override protected void populateToString(StringBuilder b) {
    lhs.populateToString(b);
    b.append(" & ");
    rhs.populateToString(b);
  }
}
