package com.squareup.trex;

import java.util.List;
import java.util.function.Consumer;

/**
 * <p>
 *   Negate a given match. That is, if a pattern used to match a token,
 *   wrapping it in this pattern will cause the token to not match, and vice
 *   versa. Note that this is only valid for a single token pattern, as the
 *   semantics of negation are unclear across multiple tokens.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class NegatedPattern extends SingleTokenPattern {

  /**
   * The pattern we are negating.
   */
  public final SingleTokenPattern negatedPattern;

  /**
   * Wrap a pattern, negating its match value.
   */
  public NegatedPattern(SingleTokenPattern negatedPattern) {
    this.negatedPattern = negatedPattern;
  }

  /** {@inheritDoc} */
  @Override protected void forEachComponent(Consumer<Pattern> fn) {
    negatedPattern.forEachComponent(fn);
    fn.accept(this);
  }

  /** {@inheritDoc} */
  @Override protected boolean matches(
      List<? extends TRexInputToken> input,
      int index,
      Matcher<? extends TRexInputToken> context) {
    return !negatedPattern.matches(input, index, context);
    // note: we don't have to register capture groups for the negated
    // pattern, since it was negated and therefore didn't itself match
  }

  /** {@inheritDoc} */
  @Override protected void populateToString(StringBuilder b) {
    b.append('!');
    negatedPattern.populateToString(b);
  }
}
