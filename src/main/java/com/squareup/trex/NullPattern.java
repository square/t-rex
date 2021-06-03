package com.squareup.trex;

import java.util.List;

/**
 * This is a pattern to check if the value at a given key is null (or no
 * such key exists -- these two cases are treated the same). For example,
 * this is the pattern for <pre>[{!key}]</pre>.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class NullPattern extends SingleTokenPattern {
  /**
   * The lookup key for the value we are matching.
   */
  public final String key;

  /**
   * Create a new null matching pattern.
   *
   * @param key See {@link #key}.
   */
  public NullPattern(String key) {
    this.key = key;
  }

  /** {@inheritDoc} */
  @Override
  protected boolean matches(List<? extends TRexInputToken> input, int index,
      Matcher<? extends TRexInputToken> context) {
    // Check that we are in bounds
    if (index < 0 || index >= input.size()) {
      return false;
    }

    // Check that the key doesn't exist
    return input.get(index).get(key) == null;
  }

  /** {@inheritDoc} */
  @Override protected void populateToString(StringBuilder b) {
    b.append('!')
     .append(key);
  }
}
