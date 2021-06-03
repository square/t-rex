package com.squareup.trex;

import java.util.List;

/**
 * <p>
 *   A terminal pattern matching a single key+value entry.
 *   This pattern matches if the value at the specified key is the same
 *   as the string value defined by this pattern, optionally modulo
 *   casing.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class StringPattern extends SingleTokenPattern {

  /**
   * The lookup key for the value we are matching.
   */
  public final String key;
  /**
   * The value specified by {@link #key} must match this value to
   * cause the pattern to match.
   */
  public final String value;
  /**
   * If true, the match must be case sensitive.
   */
  public final boolean caseSensitive;

  /** Create a new pattern */
  public StringPattern(String key, String value, boolean caseSensitive) {
    this.key = key;
    this.value = value;
    this.caseSensitive = caseSensitive;
  }

  /** {@inheritDoc} */
  @Override protected boolean matches(
      List<? extends TRexInputToken> input,
      int index,
      Matcher<? extends TRexInputToken> context) {
    if (index < 0 || index >= input.size()) {
      return false;
    }
    String actual = input.get(index).get(key);
    if (caseSensitive) {
      // Case: we are a case sensitive match
      return value.equals(actual);
    } else {
      // Case: we are a case insensitive match. On benchmarking, this
      // simplified implementation of equalsIgnoreCase() is roughly 3x faster
      // than the one in stdlib
      if (actual == null || value.length() != actual.length()) {
        return false;
      }
      for (int i = 0; i < actual.length(); ++i) {
        char a = value.charAt(i);
        char b = actual.charAt(i);
        if (a != b && Character.toUpperCase(a) != Character.toUpperCase(b)) {
          // Note that formally we should be checking Character.toLowerCase() here as
          // well. According to the associated comment in the stdlib, this is so the
          // function works on the Georgian script. For this implementation, we have
          // decided to not support Georgian patterns, and so skip that check to avoid a
          // ~50% slowdown on this function.
          return false;
        }
      }
      return true;
    }
  }

  /** {@inheritDoc} */
  @Override protected void populateToString(StringBuilder b) {
    b.append(key)
        .append(':')
        .append('"')
        .append(value.replace("\"", "\\\""))
        .append('"')
    ;
  }
}
