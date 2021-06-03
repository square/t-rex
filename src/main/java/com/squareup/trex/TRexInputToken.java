package com.squareup.trex;

/**
 * <p>
 *   A single token in our sequence to be matched. This is, conceptually, a map
 *   of string-to-string entries that can be matched by a pattern. Note that a
 *   value can be null for a given key, although this will then not match any
 *   pattern.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
@FunctionalInterface
public interface TRexInputToken {

  /**
   * <p>
   *   Get the value associated with the given key. Note that for complex
   *   patterns, this may be called many times for a given key on a token,
   *   and it's the responsibility of the implementer of this function to
   *   manage any caching that may be desired.
   * </p>
   *
   * @param key The key we are looking up for this token.
   *
   * @return The value mapped from the given key. This can be null if no value
   *         is present.
   */
  /* @Nullable */ String get(String key);
}
