package com.squareup.trex;

import java.util.List;

/**
 * <p>
 *   A simple pattern to match either the beginning or the end
 *   of a sequence. This corresponds to the ^ and $ characters
 *   in a regular expression.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class SequenceBoundaryPattern extends SingleTokenPattern {

  /**
   * One of ^ or $, for whether we're matching the beginning or
   * the end of a sequence.
   */
  private final char direction;

  /** The straightforward constructor. */
  private SequenceBoundaryPattern(char direction) {
    assert direction == '^' || direction == '$';
    this.direction = direction;
  }

  /** {@inheritDoc} */
  @Override
  protected int length() {
    return 0;
  }

  /** {@inheritDoc} */
  @Override
  protected boolean matches(List<? extends TRexInputToken> input, int index,
      Matcher<? extends TRexInputToken> context) {
    return (direction == '^' && index == 0) ||
        (direction == '$' && index == input.size());
  }

  /** {@inheritDoc} */
  @Override protected void populateToString(StringBuilder b) {
    b.append(direction);
  }

  /**
   * The pattern for matching the beginning of a sequence.
   */
  static final SequenceBoundaryPattern CARET = new SequenceBoundaryPattern('^');

  /**
   * The pattern for matching the end of a sequence.
   */
  static final SequenceBoundaryPattern DOLLAR = new SequenceBoundaryPattern('$');
}
