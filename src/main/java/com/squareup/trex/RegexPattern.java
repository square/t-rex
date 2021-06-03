package com.squareup.trex;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *   A terminal pattern matching a single key+value entry. The value
 *   is matched against a regular expression. This pattern matches
 *   if the value at the specified key is matched completely by the
 *   regex defined by this pattern.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class RegexPattern extends SingleTokenPattern {

  /**
   * A sadly package-protected method for getting the map of named groups to capture groups
   * in a regex. Calling this allows us to expose named capture groups within a regex pattern
   * to the matcher framework.
   */
  private static final /* @Nullable */ Method NAMED_GROUPS_METHOD;

  /*
   * Initialize NAMED_GROUPS_METHOD, if we can.
   */
  static {
    Method namedGroups = null;
    try {
      namedGroups = java.util.regex.Pattern.class.getDeclaredMethod("namedGroups");
      namedGroups.setAccessible(true);
    } catch (NoSuchMethodException e) {
//      log.warn("[[Reflection failed for introspecting on named groups inside of a pattern]]");
    }
    NAMED_GROUPS_METHOD = namedGroups;
  }

  /**
   * The lookup key for the value we are matching.
   */
  public final String key;
  /**
   * The regex that must match against the value at the given key.
   */
  public final java.util.regex.Pattern value;

  /**
   * A mapping from capture groups within the regex to their associated capture group index.
   */
  private final Map<String, Integer> captureGroups;

  /**
   * Create a regex pattern from a key and a regex to match the value
   * at the key.
   *
   * @param key See {@link #key}.
   * @param value See {@link #value}.
   */
  public RegexPattern(String key, java.util.regex.Pattern value) {
    this.key = key;
    this.value = value;
    Map<String, Integer> captureGroups = Collections.emptyMap();
    if (NAMED_GROUPS_METHOD != null) {
      try {
        //noinspection unchecked
        captureGroups =
            (Map<String, Integer>) NAMED_GROUPS_METHOD.invoke(value);
      } catch (IllegalAccessException | InvocationTargetException | ClassCastException e) {
//        log.warn("[[Reflection failed for get the named groups for pattern {}]]", value);
      }
    }
    this.captureGroups = Map.copyOf(captureGroups);
  }

  /** {@inheritDoc} */
  @Override protected boolean matches(
      List<? extends TRexInputToken> input,
      int index,
      Matcher<? extends TRexInputToken> context) {
    if (index < 0 || index >= input.size()) {
      return false;
    }
    /* @Nullable */ String actual = input.get(index).get(key);
    if (actual == null) {
      return false;
    } else {
      java.util.regex.Matcher matcher = value.matcher(actual);
      boolean matches = matcher.matches();
      if (matches && !captureGroups.isEmpty()) {
        for (Map.Entry<String, Integer> group : captureGroups.entrySet()) {
          String captureValue = matcher.group(group.getValue());
          if (captureValue != null) {
            String captureName = group.getKey();
            context.registerStringMatch(captureName, captureValue);
          }
        }
      }
      return matches;
    }
  }

  /** {@inheritDoc} */
  @Override protected void populateToString(StringBuilder b) {
    b.append(key)
        .append(':')
        .append('/')
        .append(value)
        .append('/');
  }
}
