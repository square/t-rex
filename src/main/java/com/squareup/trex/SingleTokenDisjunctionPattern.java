package com.squareup.trex;

import java.util.List;
import java.util.function.Consumer;

/**
 * A simplified implementation of a disjunction for just a single token.
 */
class SingleTokenDisjunctionPattern extends SingleTokenPattern {

  /**
   * The left hand side to match.
   */
  public final SingleTokenPattern lhs;
  /**
   * The right hand side to match.
   */
  public final SingleTokenPattern rhs;
  /**
   * The length of this disjunction match. This should be either 0
   * or 1, depending on whether this pattern actually consumes
   * a token or not.
   */
  private final int length;

  /** Create a single token disjunction from two single tokens to join */
  SingleTokenDisjunctionPattern(SingleTokenPattern lhs, SingleTokenPattern rhs) {
    this.lhs = lhs;
    this.rhs = rhs;
    this.length = lhs.length();
    assert length == rhs.length()
        : "We cannot have a single token disjunction pattern if lengths mismatch";
  }

  /** {@inheritDoc} */
  @Override protected int length() {
    return length;
  }

  /** {@inheritDoc} */
  @Override protected boolean matches(List<? extends TRexInputToken> input, int index,
      Matcher<? extends TRexInputToken> context) {
    if (lhs.matches(input, index, context)) {
      lhs.registerMatch(index, index + length(), context);
      return true;
    } else if (rhs.matches(input, index, context)) {
      rhs.registerMatch(index, index + length(), context);
      return true;
    } else {
      return false;
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
    lhs.populateToString(b);
    b.append(" | ");
    rhs.populateToString(b);
  }
}
