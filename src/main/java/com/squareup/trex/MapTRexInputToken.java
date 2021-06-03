package com.squareup.trex;

import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple implementation of an input token that does a lookup in
 * a Java map. This can be used to create a matcher against a
 * list of maps, where each element of the list corresponds to the
 * information in a token.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class MapTRexInputToken implements TRexInputToken {

  /**
   * The Logger for this class
   */
  private static final Logger log = LoggerFactory.getLogger(MapTRexInputToken.class);

  /** The Map that's backing this token */
  private final Map<String, String> token;

  /**
   * Create a token backed by a Java Map.
   *
   * @param token The Map that's backing the values in this token
   */
  public MapTRexInputToken(Map<String, String> token) {
    this.token = token;
  }

  /** {@inheritDoc} */
  @Override public String get(String key) {
    return token.get(key);
  }

  /** {@inheritDoc} */
  @Override public String toString() {
    return token.toString();
  }

  /** {@inheritDoc} */
  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MapTRexInputToken that = (MapTRexInputToken) o;
    return token.equals(that.token);
  }

  /** {@inheritDoc} */
  @Override public int hashCode() {
    return Objects.hash(token);
  }
}
