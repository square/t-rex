package com.squareup.trex;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test the {@link RegexPattern} class.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class RegexPatternTest {
  /**
   * A dummy sentence we can use in our tests.
   */
  private List<CoreLabelInputToken> tokens = NLPUtils.tokenize("hello world").get(0)
      .stream().map(CoreLabelInputToken::new).collect(Collectors.toList());

  /**
   * Test the baseline case for the {@link RegexPattern#matches(List, int, Matcher)} method.
   */
  @Test void matchesBaseCase() {
    RegexPattern pattern = new RegexPattern("default", java.util.regex.Pattern.compile("hello"));
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "hello world" */);
    assertTrue(pattern.matches(tokens, 0, matcher), "Baseline check");
  }

  /**
   * Test that we don't match if our index is out of bounds.
   */
  @Test void matchesOutOfBounds() {
    RegexPattern pattern = new RegexPattern("default", java.util.regex.Pattern.compile("hello"));
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "hello world" */);
    assertFalse(pattern.matches(tokens, -1, matcher),
        "We should not match on negative indices");
    assertFalse(pattern.matches(tokens, tokens.size(), matcher),
        "We should not match on out of bounds indices (>=input.size)");
  }

  /**
   * Test that we don't match if we don't have the appropriate key.
   */
  @Test void matchesNoKey() {
    RegexPattern pattern = new RegexPattern("nokey", java.util.regex.Pattern.compile("hello"));
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "52 cards" */);
    assertFalse(pattern.matches(tokens, 0, matcher),
        "We have no value in our input token with key 'nokey'");
  }

  /**
   * Test that we don't match if we don't have the appropriate key.
   */
  @Test void regexCaptureGroup() {
    RegexPattern pattern
        = new RegexPattern("default", java.util.regex.Pattern.compile("(?<foo>hello)"));
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "52 cards" */);
    assertTrue(pattern.matches(tokens, 0, matcher), "Our regex should match");
    assertEquals(Collections.singletonMap("foo", "hello"), matcher.stringCaptureGroups());
  }

  /**
   * Test the toString method of the pattern.
   */
  @Test void testToString() {
    RegexPattern pattern = new RegexPattern("default", java.util.regex.Pattern.compile("hello"));
    assertEquals("[default:/hello/]", pattern.toString());
  }

}
