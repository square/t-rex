package com.squareup.trex;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A test for the {@link Matcher} class and subclasses
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class MatcherTest {
  private List<CoreLabelInputToken> tokens = NLPUtils.tokenize("a b c d e").get(0)
      .stream().map(CoreLabelInputToken::new).collect(Collectors.toList());

  /**
   * Tests for the capture group class
   */
  @SuppressWarnings("InnerClassMayBeStatic")
  @Nested
  class CaptureGroupTest {

    /**
     * Test the methods to get indices from a capture group.
     */
    @Test void getIndices() {
      Matcher.CaptureGroup<CoreLabelInputToken> group = new Matcher.CaptureGroup<>(0, 0, 5, tokens);
      assertEquals(0, group.getBeginInclusive());
      assertEquals(5, group.getEndExclusive());
      group = new Matcher.CaptureGroup<>(0, 3, 4, tokens);
      assertEquals(3, group.getBeginInclusive());
      assertEquals(4, group.getEndExclusive());
    }

    /**
     * Test the methods to get the matching token subspan from a capture group.
     */
    @Test void matchedTokens() {
      Matcher.CaptureGroup<CoreLabelInputToken> group = new Matcher.CaptureGroup<>(0, 0, 5, tokens);
      assertEquals(tokens.subList(0, 5), group.matchedTokens());
      group = new Matcher.CaptureGroup<>(0, 3, 4, tokens);
      assertEquals(tokens.subList(3, 4), group.matchedTokens());
    }

    /**
     * Test that equals and hashcode are consistent for a capture group.
     */
    @Test void equalsHashCode() {
      Matcher.CaptureGroup<CoreLabelInputToken> group = new Matcher.CaptureGroup<>(0, 1, 4, tokens);
      Matcher.CaptureGroup<CoreLabelInputToken> copy = new Matcher.CaptureGroup<>(0, 1, 4, tokens);
      assertEquals(group, group);
      assertEquals(group, copy);
      assertEquals(group.hashCode(), copy.hashCode());
    }

    /**
     * Test the toString method on a capture group.
     */
    @Test void testToString() {
      Matcher.CaptureGroup<CoreLabelInputToken> group = new Matcher.CaptureGroup<>(0, 1, 4, tokens);
      assertEquals("[1,4)", group.toString());
    }
  }

  /**
   * Test that we throw exceptions from a matcher that we haven't
   * initialized yet.
   */
  @Test void emptyMatcher() {
    Pattern p = Pattern.compile("a b c");
    Matcher<CoreLabelInputToken> m = p.matcher(tokens);
    assertThrows(IllegalStateException.class, m::start);
    assertThrows(IllegalStateException.class, m::end);
    assertThrows(IllegalStateException.class, () -> m.group(0));
    assertThrows(IllegalStateException.class, () -> m.group("name"));
  }

  /**
   * Test that we can reset a matcher.
   */
  @Test void reset() {
    Pattern p = Pattern.compile("/[abc]/");
    Matcher<CoreLabelInputToken> m = p.matcher(tokens);

    // Find the first two matches
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(1, m.end());
    assertTrue(m.find());
    assertEquals(1, m.start());
    assertEquals(2, m.end());

    // Reset
    m.reset();

    // Check that we start from the beginning
    assertTrue(m.find());
    assertEquals(0, m.start());
    assertEquals(1, m.end());
  }

  /**
   * Test our transient iterator helper function.
   */
  @Test void transientIterator() {
    Pattern p = Pattern.compile("a b c");
    Matcher<CoreLabelInputToken> m = p.matcher(tokens);

    SingleValueIterator iter = m.transientIterator(4);
    assertTrue(iter.hasNext());
    assertEquals(4, iter.nextInt());
    assertFalse(iter.hasNext());

    SingleValueIterator empty = m.transientIterator(-1);
    assertSame(iter, empty, "We should not create new iterator instances");
    assertFalse(empty.hasNext());
  }

  /**
   * Test that the find() method doesn't return duplicate matched spans.
   */
  @Test void duplicateMatchesInFind() {
    Pattern p = Pattern.compile("(a{1} | a{2}) (a{2} | a{1})");
    Matcher<CoreLabelInputToken> m = CoreNLPUtils.matcher(p, NLPUtils.tokenize("a a a a a").get(0));
    // There should be 3 matches from index 0
    assertTrue(m.find(), "We should find a{1} a{2}");
    assertEquals(0, m.start());
    assertEquals(3, m.end());
    assertTrue(m.find(), "We should find a{1} a{1}");
    assertEquals(0, m.start());
    assertEquals(2, m.end());
    assertTrue(m.find(), "We should find a{2} a{2}");
    assertEquals(0, m.start());
    assertEquals(4, m.end());
    // The fourth match from index 0 would be a{2} a{1}, but this should be filtered out
    // We then start matching from index 1
    assertTrue(m.find(), "We should now start matching from index 1");
    assertEquals(1, m.start(), "We should have started matching from index 1");
    assertEquals(4, m.end());

  }

  /**
   * Test that we can match a single-token capture group.
   */
  @Test void singleTokenNamedGroup() {
    Pattern p = Pattern.compile("(?<a> a) b c d e");
    Matcher<CoreLabelInputToken> m = p.matcher(tokens);
    assertTrue(m.find());
    assertNotNull(m.group("a"));
  }

  /**
   * Test that we can match a nested single-token capture group.
   */
  @Test void nestedSingleTokenNamedGroup() {
    Pattern p = Pattern.compile("((?<a> a) b) c d e");
    Matcher<CoreLabelInputToken> m = p.matcher(tokens);
    assertTrue(m.find());
    assertNotNull(m.group("a"));
  }

  /**
   * Test that we can match a nested capture group in a complex
   * disjunction expression.
   */
  @Test void nestedSingleTokenNamedGroupDisjunction() {
    Pattern p = Pattern.compile("((?<a> a) | b) b c d e");
    Matcher<CoreLabelInputToken> m = p.matcher(tokens);
    assertTrue(m.find());
    assertNotNull(m.group("a"));
  }

  /**
   * Test that we can match a nested capture group in a complex
   * conjunction expression.
   */
  @Test void nestedSingleTokenNamedGroupConjunction() {
    Pattern p = Pattern.compile("((?<a1> a) & (?<a2> a)) b c d e");
    Matcher<CoreLabelInputToken> m = p.matcher(tokens);
    assertTrue(m.find());
    assertNotNull(m.group("a1"));
    assertNotNull(m.group("a2"));
  }

  /**
   * Test that we can return all of the named capture groups
   * in a match
   */
  @Test void namedCaptureGroups() {
    Pattern p = Pattern.compile("((?<a> a) | (?<b> b)) b (?<c> c)");
    Matcher<CoreLabelInputToken> m = p.matcher(tokens);
    assertTrue(m.find());
    Matcher.CaptureGroup<CoreLabelInputToken> groupA = m.group("a");
    assertNotNull(groupA);
    Matcher.CaptureGroup<CoreLabelInputToken> groupC = m.group("c");
    assertNotNull(groupC);
    assertEquals(Map.ofEntries(
        Map.entry("a", groupA),
        Map.entry("c", groupC)
    ), m.namedCaptureGroups());
  }

}
