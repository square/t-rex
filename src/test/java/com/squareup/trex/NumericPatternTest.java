package com.squareup.trex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NumericPattern}.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class NumericPatternTest {

  /**
   * A dummy sentence we can use in our tests.
   */
  private List<CoreLabelInputToken> tokens = NLPUtils.tokenize("52 cards").get(0)
      .stream().map(CoreLabelInputToken::new).collect(Collectors.toList());

  /**
   * Test the {@link NumericPattern#parseInt(String, Matcher)} method.
   */
  @Test void parseInt() {
    Matcher<? extends CoreLabelInputToken> matcher
        = new Matcher<>(Pattern.compile("[]"), Collections.emptyList());

    // Test positive values
    assertEquals(0, NumericPattern.parseInt("0", matcher));
    assertFalse(matcher.transientError);
    assertEquals(1, NumericPattern.parseInt("1", matcher));
    assertFalse(matcher.transientError);
    for (int i = 0; i < 1000; ++i) {
      assertEquals(i, NumericPattern.parseInt(Integer.toString(i), matcher));
      assertFalse(matcher.transientError);
    }
    // Negative values all become -1
    assertEquals(-1, NumericPattern.parseInt("-1", matcher));
    assertFalse(matcher.transientError);
    assertEquals(-5, NumericPattern.parseInt("-5", matcher));
    assertFalse(matcher.transientError);
    // Error cases
    assertEquals(0, NumericPattern.parseInt("foo", matcher));
    assertTrue(matcher.transientError);
    assertEquals(0, NumericPattern.parseInt("2.54", matcher));
    assertTrue(matcher.transientError);
    assertEquals(0, NumericPattern.parseInt("1e10", matcher));
    assertTrue(matcher.transientError);
    assertEquals(0, NumericPattern.parseInt("-", matcher));
    assertTrue(matcher.transientError);
    // Boundary cases
    long maxInt = Integer.MAX_VALUE;
    assertEquals(Integer.MAX_VALUE, NumericPattern.parseInt(Long.toString(maxInt), matcher));
    assertFalse(matcher.transientError);
    assertEquals(0, NumericPattern.parseInt(Long.toString(maxInt + 1), matcher));
    assertTrue(matcher.transientError);
    assertEquals(0, NumericPattern.parseInt(Long.toString(Long.MAX_VALUE), matcher));
    assertTrue(matcher.transientError);
    assertEquals(Integer.MIN_VALUE + 1,
        NumericPattern.parseInt(Long.toString(Integer.MIN_VALUE + 1), matcher));
    assertFalse(matcher.transientError);
    assertEquals(Integer.MIN_VALUE,
        NumericPattern.parseInt(Long.toString(Integer.MIN_VALUE), matcher));
    assertFalse(matcher.transientError);
  }

  /**
   * Test the baseline case for the {@link NumericPattern#matches(List, int, Matcher)} method.
   */
  @Test void matchesBaseCase() {
    NumericPattern pattern = new NumericPattern("default", 10, NumericPattern.Operator.GT);
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "52 cards" */);
    assertTrue(pattern.matches(tokens, 0, matcher), "Baseline check");
  }

  /**
   * Test that we don't match if our index is out of bounds.
   */
  @Test void matchesOutOfBounds() {
    NumericPattern pattern = new NumericPattern("default", 10, NumericPattern.Operator.GT);
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "52 cards" */);
    assertFalse(pattern.matches(tokens, -1, matcher),
        "We should not match on negative indices");
    assertFalse(pattern.matches(tokens, tokens.size(), matcher),
        "We should not match on out of bounds indices (>=input.size)");
  }

  /**
   * Test that we don't match if we don't have the appropriate key.
   */
  @Test void matchesNoKey() {
    NumericPattern pattern = new NumericPattern("nokey", 10, NumericPattern.Operator.GT);
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "52 cards" */);
    assertFalse(pattern.matches(tokens, 0, matcher),
        "We have no value in our input token with key 'nokey'");
  }

  /**
   * Test that we don't match if we can't parse our value as an integer.
   */
  @Test void matchesNotInt() {
    NumericPattern pattern = new NumericPattern("default", 10, NumericPattern.Operator.GT);
    Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "52 cards" */);
    assertFalse(pattern.matches(tokens, 1 /* cards */, matcher),
        "The token at input 1 ('cards') is not an integer and so shouldn't match.");
  }

  /**
   * Ensure that the a pattern with the given operator + value
   * matches token 0 of {@link #tokens}, which has a value of 52.
   */
  private DynamicTest assertMatch(NumericPattern.Operator op, int value) {
    return DynamicTest.dynamicTest("52 " + op.toString() + " " + value, () -> {
      NumericPattern pattern = new NumericPattern("default", value, op);
      Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "52 cards" */);
      assertTrue(pattern.matches(tokens, 0, matcher));
    });
  }

  /**
   * Ensure that the a pattern with the given operator + value
   * does not match token 0 of {@link #tokens}, which has a value of 52.
   */
  private DynamicTest assertNotMatch(NumericPattern.Operator op, int value) {
    return DynamicTest.dynamicTest("!(52 " + op.toString() + " " + value + ")", () -> {
      NumericPattern pattern = new NumericPattern("default", value, op);
      Matcher<CoreLabelInputToken> matcher = new Matcher<>(pattern, tokens /* "52 cards" */);
      assertFalse(pattern.matches(tokens, 0, matcher));
    });
  }

  /**
   * Test that all of our operators behave as they should.
   */
  @TestFactory Iterable<DynamicTest> operatorBehavior() {
    return Arrays.asList(
        assertMatch(NumericPattern.Operator.LT, 53),
        assertMatch(NumericPattern.Operator.LT, Integer.MAX_VALUE),
        assertNotMatch(NumericPattern.Operator.LT, 52),
        assertMatch(NumericPattern.Operator.LTE, 53),
        assertMatch(NumericPattern.Operator.LTE, 52),
        assertNotMatch(NumericPattern.Operator.LTE, 51),
        assertMatch(NumericPattern.Operator.GT, 51),
        assertMatch(NumericPattern.Operator.GT, 0),
        assertMatch(NumericPattern.Operator.GT, -1),
        assertMatch(NumericPattern.Operator.GT, Integer.MIN_VALUE),
        assertNotMatch(NumericPattern.Operator.GT, 52),
        assertMatch(NumericPattern.Operator.GTE, 51),
        assertMatch(NumericPattern.Operator.GTE, 52),
        assertNotMatch(NumericPattern.Operator.GTE, 53),
        assertMatch(NumericPattern.Operator.EQ, 52),
        assertNotMatch(NumericPattern.Operator.EQ, 51),
        assertNotMatch(NumericPattern.Operator.EQ, 53),
        assertNotMatch(NumericPattern.Operator.EQ, Integer.MIN_VALUE),
        assertNotMatch(NumericPattern.Operator.EQ, Integer.MAX_VALUE),
        assertMatch(NumericPattern.Operator.NEQ, 51),
        assertMatch(NumericPattern.Operator.NEQ, 53),
        assertMatch(NumericPattern.Operator.NEQ, Integer.MAX_VALUE),
        assertMatch(NumericPattern.Operator.NEQ, Integer.MIN_VALUE)
    );
  }

  /**
   * Test the toString method of numeric pattern.
   */
  @Test void testToString() {
    assertEquals("[key<10]",
        new NumericPattern("key", 10, NumericPattern.Operator.LT).toString());
    assertEquals("[key>10]",
        new NumericPattern("key", 10, NumericPattern.Operator.GT).toString());
    assertEquals("[key<=10]",
        new NumericPattern("key", 10, NumericPattern.Operator.LTE).toString());
    assertEquals("[key>=10]",
        new NumericPattern("key", 10, NumericPattern.Operator.GTE).toString());
    assertEquals("[key==10]",
        new NumericPattern("key", 10, NumericPattern.Operator.EQ).toString());
    assertEquals("[key!=10]",
        new NumericPattern("key", 10, NumericPattern.Operator.NEQ).toString());
  }

  /**
   * Make sure that the toString method renders all the ops as unique,
   * and that we're not invoking the default option anywhere.
   */
  @Test void toStringRendersAllOps() {
    assertEquals(NumericPattern.Operator.values().length,
        Arrays.stream(NumericPattern.Operator.values())
            .map(op -> new NumericPattern("key", 10, op).toString())
            .count());
  }
}
