package com.squareup.trex;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test {@link SequencePattern}.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class SequencePatternTest {
  /**
   * A dummy sentence we can use in our tests.
   */
  private List<CoreLabelInputToken> tokens = NLPUtils.tokenize("hello world").get(0)
      .stream().map(CoreLabelInputToken::new).collect(Collectors.toList());

  /**
   * Test creating a pattern that doesn't have either a component list
   * or a single component repeated.
   */
  @Disabled("Disabled for CI since the CI runner won't disable assertions")
  @Test void invalidPattern() {
    // Create an invalid pattern, bypassing the assertion that
    // would prevent this. Note that this is mildly non-threadsafe:
    // if another thread is looking for an AssertionError during the
    // execution of this block, that error won't fire.
    SequencePattern.class.getClassLoader().setDefaultAssertionStatus(false);
    SequencePattern pattern;
    try {
      //noinspection ConstantConditions
      pattern = new SequencePattern(null, null, 0, 1, false);
    } finally {
      SequencePattern.class.getClassLoader().setDefaultAssertionStatus(true);
    }

    // Check that we can still toString it
    assertEquals("???", pattern.toString());

    // Check that it doesn't match anything
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "hello world" */);
    PrimitiveIterator.OfInt matches = pattern.consume(tokens, 0, matcher);
    assertFalse(matches.hasNext());
  }

  /**
   * Test that we throw an exception if our iterator is empty.
   */
  @Test void iteratorEmpty() {
    SequencePattern pattern = new SequencePattern(
        Pattern.compile("hello"), null, 1, 1, false);
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "hello world" */);
    PrimitiveIterator.OfInt matches = pattern.consume(tokens, 0, matcher);

    assertTrue(matches.hasNext(), "We should have one match");
    matches.nextInt();
    assertFalse(matches.hasNext(), "We should only have had one match");
    assertThrows(NoSuchElementException.class, matches::nextInt,
        "Trying to get a match should throw an exception");
  }
}
