package com.squareup.trex;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NullPattern}.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class NullPatternTest {
  /**
   * A dummy sentence we can use in our tests.
   */
  private List<CoreLabelInputToken> tokens = NLPUtils.tokenize("hello world").get(0)
      .stream().map(CoreLabelInputToken::new).collect(Collectors.toList());

  /**
   * Test the baseline case for the {@link NullPattern#matches(List, int, Matcher)} method.
   */
  @Test void matchesBaseCase() {
    NullPattern pattern = new NullPattern("nokey");
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "hello world" */);
    assertTrue(pattern.matches(tokens, 0, matcher), "Baseline check");
  }

  /**
   * Test that we don't match if our index is out of bounds.
   */
  @Test void matchesOutOfBounds() {
    NullPattern pattern = new NullPattern("nokey");
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "hello world" */);
    assertFalse(pattern.matches(tokens, -1, matcher),
        "We should not match on negative indices");
    assertFalse(pattern.matches(tokens, tokens.size(), matcher),
        "We should not match on out of bounds indices (>=input.size)");
  }

  /**
   * Test the toString method of the pattern.
   */
  @Test void testToString() {
    NullPattern pattern = new NullPattern("nokey");
    assertEquals("[!nokey]", pattern.toString());
  }

}
