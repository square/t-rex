package com.squareup.trex;

import java.util.List;

/**
 * <p>
 *   A pattern that matches any single token. This is the implementation
 *   of the [] construct.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class WildcardPattern extends SingleTokenPattern {

  /** {@inheritDoc} */
  @Override protected boolean matches(List<? extends TRexInputToken> input, int index,
      Matcher<? extends TRexInputToken> context) {
    return index >= 0 && index < input.size();
  }

  /**
   * A singleton instance of this pattern, because there's not point creating
   * a new instance every time we want to use it.
   */
  static WildcardPattern INSTANCE = new WildcardPattern();

  /** {@inheritDoc} */
  @Override protected void populateToString(StringBuilder b) { /* empty */ }
}
