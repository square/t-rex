package com.squareup.trex;

import edu.stanford.nlp.ling.CoreLabel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test the {@link Pattern} class.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class PatternTest {

  /**
   * The Logger for this class
   */
  protected final Logger log = LoggerFactory.getLogger(this.getClass().getName());

  /**
   * A helper to ensure that a given pattern matches a given sentence.
   * Note that the sentence is only tokenized, for efficiency.
   */
  private DynamicTest matches(String pattern, String sentence, boolean caseSensitive) {
    List<CoreLabel> tokens = NLPUtils.tokenize(sentence).get(0);
    Pattern compiledPattern = Pattern.compile(pattern, caseSensitive);
    return DynamicTest.dynamicTest("/" + pattern + "/ should match '" + sentence + "'",
        () -> assertTrue(CoreNLPUtils.matcher(compiledPattern, tokens).matches()));
  }

  /**
   * @see #matches(String, String, boolean)
   */
  private DynamicTest matches(String pattern, String sentence) {
    return matches(pattern, sentence, false);
  }

  /**
   * A helper to ensure that a given pattern DOES NOT MATCH a given sentence.
   * Note that the sentence is only tokenized, for efficiency.
   */
  private DynamicTest notMatches(String pattern, String sentence, boolean caseSensitive) {
    List<CoreLabel> tokens = NLPUtils.tokenize(sentence).get(0);
    Pattern compiledPattern = Pattern.compile(pattern, caseSensitive);
    return DynamicTest.dynamicTest("/" + pattern + "/ should not match '" + sentence + "'",
        () -> assertFalse(CoreNLPUtils.matcher(compiledPattern, tokens).matches()));
  }

  /**
   * @see #notMatches(String, String, boolean)
   */
  private DynamicTest notMatches(String pattern, String sentence) {
    return notMatches(pattern, sentence, false);
  }

  /**
   * Test that we can parse a simple pattern without crashing or
   * returning null.
   */
  @Test void compileCrashTest() {
    assertNotNull(Pattern.compile("hello"));
    assertNotNull(Pattern.compile("hello", false, Collections.emptyMap()));
    assertNotNull(Pattern.compile("hello", false, "default_key"));
  }

  /**
   * Test compiling+matching an empty pattern.
   */
  @Test void emptyPattern() {
    Pattern empty = Pattern.compile("");
    assertFalse(CoreNLPUtils.matcher(empty, NLPUtils.tokenize("hello").get(0)).matches());
    assertTrue(CoreNLPUtils.matcher(empty, Collections.emptyList()).matches());
  }

  /**
   * Test the toString method on an empty pattern.
   */
  @Test void emptyPatternToString() {
    assertEquals("", Pattern.compile("").toString());
    StringBuilder b = new StringBuilder();
    Pattern.compile("").populateToString(b);
    assertEquals("", b.toString());
  }

  /**
   * This function parses a pattern and then writes it out to a
   * string via {@link Pattern#toString()}; it then checks that the
   * written string is the expected pattern.
   *
   * @param expected The expected toString for the pattern.
   * @param pattern The pattern we're parsing.
   *
   * @return A dynamic test to ensure that parsing this pattern is lossless.
   */
  private static DynamicTest losslessToString(String expected, String pattern) {
    return DynamicTest.dynamicTest(pattern, () -> {
      Pattern p = Pattern.compile(pattern, false);
      assertEquals(expected, p.toString(), "Pattern's toString is lossy");
    });
  }

  /**
   * This function parses a pattern and then writes it out to a
   * string via {@link Pattern#toString()}; it then checks that the
   * written string is the same as the input pattern. Thus, it checks
   * that the toString method on Pattern is lossless.
   *
   * @param pattern The pattern we're parsing.
   *
   * @return A dynamic test to ensure that parsing this pattern is lossless.
   */
  private static DynamicTest losslessToString(String pattern) {
    return losslessToString(pattern, pattern);
  }

  /**
   * Tests that {@link Pattern#toString()} is lossless.
   *
   * @see #losslessToString(String)
   */
  @TestFactory Iterable<DynamicTest> losslessToString() {
    return Arrays.asList(
        losslessToString("[default:\"hello\"]"),
        losslessToString("[key:\"hello\"]"),
        losslessToString("[key:/h.llo/]"),
        losslessToString("[key:/a\\/b/]"),
        losslessToString("[default:\"hello\"]", "hello"),
        losslessToString("[default:\"hello\"]", "\"hello\""),
        losslessToString("[default:\"hello\"]", "/hello/"),  // note[gabor]: regex auto-simplifies
        losslessToString("[default:/he.lo/]", "/he.lo/"),
        losslessToString("[key:\"hello\"] [key:\"world\"]"),
        losslessToString("[default:\"hello\"] [default:\"world\"]", "hello world"),
        losslessToString("([key:\"hello\"] [key:\"world\"])"),
        losslessToString("(?<name> [key:\"hello\"] [key:\"world\"])"),
        losslessToString("(?<name> [key:\"hello\"]) [key:\"world\"]"),
        losslessToString("[key:\"hello\" & foo:\"bar\"]"),
        losslessToString("[key:\"hello\" | foo:\"bar\"]"),
        losslessToString("[!key]"),
        losslessToString("[key!=7 & key!=5]"),
        losslessToString("[key>7 & key>=5]"),
        losslessToString("[key<7 & key<=5]"),
        losslessToString("[key:\"hello\"] [foo:\"bar\"]"),
        losslessToString("[key:\"hello\"] [a:\"b\"] & [foo:\"bar\"]"),
        losslessToString("[key:\"hello\"] [a:\"b\"] | [foo:\"bar\"]"),
        losslessToString("[key:\"hello\"]+"),
        losslessToString("[default:\"hello\"]+", "hello+"),
        losslessToString("[key:\"hello\"]*"),
        losslessToString("[default:\"hello\"]*", "hello*"),
        losslessToString("[key:\"hello\"]?"),
        losslessToString("[default:\"hello\"]?", "hello?"),
        losslessToString("[key:\"hello\"]{2}"),
        losslessToString("[default:\"hello\"]{2}", "hello{2}"),
        losslessToString("[key:\"hello\"]{2,}"),
        losslessToString("[key:\"hello\"]{2,3}"),
        losslessToString("[key:\"hello\"]+?"),
        losslessToString("[default:\"hello\" | default:\"world\"]", "hello | world"),
        losslessToString("[default:\"hello\" & default:\"world\"]", "hello & world"),
        // note[gabor]: this is documenting a known deficiency in the toString method.
        // This *should* but does not retain that "(hello)" has a capture group around it.
        // This is a purely cosmetic bug.
        losslessToString("[default:\"hello\" | default:\"world\"]", "(hello) | world")
    );
  }

  /**
   * Test that the given pattern decomposes into the given components
   * via the {@link Pattern#forEachComponent(Consumer)} variable.
   *
   * @param pattern The pattern we're testing.
   * @param components The string value of the components we're testing.
   *
   * @return A dynamic test ensuring that the pattern decomposes as expected.
   */
  private static DynamicTest decomposition(String pattern, String... components) {
    return DynamicTest.dynamicTest(pattern, () -> {
      Pattern p = Pattern.compile(pattern, false);
      AtomicInteger index = new AtomicInteger(0);  // atomicity is not required
      p.forEachComponent(component -> {
        assertTrue(components.length > index.get(), "Pattern has more componetns than expected");
        assertEquals(components[index.getAndIncrement()], component.toString(),
          "Component " + index.get() + " (of " + components.length + ") does not match");
      });
    });
  }

  /**
   * Check that the {@link Pattern#forEachComponent(Consumer)} function
   * faithfully loops through all of the components in a pattern.
   */
  @TestFactory Iterable<DynamicTest> forEachComponent() {
    return Arrays.asList(
        decomposition("hello", "[default:\"hello\"]"),
        decomposition("hello+",
            "[default:\"hello\"]",
            "[default:\"hello\"]+"),
        decomposition("hello+ world",
            "[default:\"hello\"]",
            "[default:\"hello\"]+",
            "[default:\"world\"]",
            "[default:\"hello\"]+ [default:\"world\"]"),
        decomposition("hello | world",
            "[default:\"hello\"]",
            "[default:\"world\"]",
            "[default:\"hello\" | default:\"world\"]"),
        decomposition("hello & world",
            "[default:\"hello\"]",
            "[default:\"world\"]",
            "[default:\"hello\" & default:\"world\"]"),
        decomposition("hello+ | world*",
            "[default:\"hello\"]",
            "[default:\"hello\"]+",
            "[default:\"world\"]",
            "[default:\"world\"]*",
            "[default:\"hello\"]+ | [default:\"world\"]*"),
        decomposition("hello+ & world*",
            "[default:\"hello\"]",
            "[default:\"hello\"]+",
            "[default:\"world\"]",
            "[default:\"world\"]*",
            "[default:\"hello\"]+ & [default:\"world\"]*"),
        decomposition("[key:a | !key:b | !foo]",
            "[key:\"a\"]",
            "[key:\"b\"]",
            "[!key:\"b\"]",
            "[key:\"a\" | !key:\"b\"]",
            "[!foo]",
            "[key:\"a\" | !key:\"b\" | !foo]"),
        decomposition("(hello) & world",
            "([default:\"hello\"])",
            "[default:\"world\"]",
            "[default:\"hello\" & default:\"world\"]")
    );
  }

  // region: compile pattern

  /**
   * Test that we can compile all the rules and branches on the rules.
   */
  @TestFactory Iterable<DynamicTest> compileAllRules() {
    return Arrays.asList(
        DynamicTest.dynamicTest("UnquotedStringLiteral",
            () -> assertNotNull(Pattern.compile("hello"))),
        DynamicTest.dynamicTest("QuotedStringLiteral",
            () -> assertNotNull(Pattern.compile("\"hello\""))),
        DynamicTest.dynamicTest("RegexLiteral",
            () -> assertNotNull(Pattern.compile("/hello/"))),
        DynamicTest.dynamicTest("Number",
            () -> assertNotNull(Pattern.compile("42"))),
        DynamicTest.dynamicTest("key_value_pair",
            () -> assertNotNull(Pattern.compile("[{foo:bar}]"))),
        DynamicTest.dynamicTest("token_body_atom_paren",
            () -> assertNotNull(Pattern.compile("[({foo:bar})]"))),
        DynamicTest.dynamicTest("token_body_atom_negated",
            () -> assertNotNull(Pattern.compile("[!{foo:bar}]"))),
        DynamicTest.dynamicTest("token_body_conjunction",
            () -> assertNotNull(Pattern.compile("[{foo:bar} & {a:b}]"))),
        DynamicTest.dynamicTest("token_body_disjunction",
            () -> assertNotNull(Pattern.compile("[{foo:bar} | {a:b}]"))),
        DynamicTest.dynamicTest("literal_token",
            () -> assertNotNull(Pattern.compile("hello"))),
        DynamicTest.dynamicTest("complex_token",
            () -> assertNotNull(Pattern.compile("[{foo:bar}]"))),
        DynamicTest.dynamicTest("wildcard_token",
            () -> assertNotNull(Pattern.compile("[]"))),
        DynamicTest.dynamicTest("repeat_atom_bounded",
            () -> assertNotNull(Pattern.compile("hello{5,10}"))),
        DynamicTest.dynamicTest("repeat_atom_unbounded",
            () -> assertNotNull(Pattern.compile("hello{5,}"))),
        DynamicTest.dynamicTest("repeat_atom_exact",
            () -> assertNotNull(Pattern.compile("hello{5}"))),
        DynamicTest.dynamicTest("plus_atom",
            () -> assertNotNull(Pattern.compile("hello+"))),
        DynamicTest.dynamicTest("star_atom",
            () -> assertNotNull(Pattern.compile("hello*"))),
        DynamicTest.dynamicTest("qmark_atom",
            () -> assertNotNull(Pattern.compile("hello?"))),
        DynamicTest.dynamicTest("paren_element",
            () -> assertNotNull(Pattern.compile("(hello world)"))),
        DynamicTest.dynamicTest("atom_token",
            () -> assertNotNull(Pattern.compile("hello"))),
        DynamicTest.dynamicTest("variable",
            () -> assertNotNull(Pattern.compile("$foo", false, "text",
                Collections.singletonMap("foo", Pattern.compile("hello"))))),
        DynamicTest.dynamicTest("pattern",
            () -> assertNotNull(Pattern.compile("hello world"))),
        DynamicTest.dynamicTest("conjunction",
            () -> assertNotNull(Pattern.compile("hello world & goodbye world"))),
        DynamicTest.dynamicTest("disjunction",
            () -> assertNotNull(Pattern.compile("to be | not to be"))),
        DynamicTest.dynamicTest("parenthetical",
            () -> assertNotNull(Pattern.compile("(hello world) \"!\""))),
        DynamicTest.dynamicTest("named parenthetical",
            () -> assertNotNull(Pattern.compile("(?<foo> hello world) \"!\""))),
        DynamicTest.dynamicTest("named parenthetical (legacy",
            () -> assertNotNull(Pattern.compile("(?$foo hello world) \"!\"")))
    );
  }

  /**
   * Test that compilation fails when it should
   */
  @Test void compilationFailures() {
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("[\"key\":\"value\"]"),
        "This is a syntax error");
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("$NOVAR"),
        "We should not parse patterns with unbound variables");
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("hello{-1}"),
        "We cannot match a negative number of times");
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("hello{-1,}"),
        "We cannot match a negative number of times");
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("hello{-1,5}"),
        "We cannot match a negative number of times");
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("hello{0,-5}"),
        "We cannot match a negative number of times");
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("hello{4,3}"),
        "The upper bound of a match must be more than the lower bound");
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("hello{5000000000,6000000000}"),
        "The lower bound is not a valid integer");
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("hello{0,6000000000}"),
        "The upper bound is not a valid integer");
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("hello{5000000000}"),
        "The match count is not a valid integer");
    assertThrows(IllegalArgumentException.class,
        () -> Pattern.compile("hello{5000000000,}"),
        "The lower bound is not a valid integer");
  }

  /**
   * A dummy class to extract a numeric context from a pattern. This is used
   * in {@link #keyValueNumericSpecialCases()} to test the corner cases of
   * the visitor.
   */
  private static class RecordingNumericVisitor extends TRexBaseVisitor<SingleTokenPattern> {
    private TRexParser.Key_value_numericContext ctx;
    private TRexParser.Numeric_opContext opCtx;
    /** {@inheritDoc} */
    @Override public SingleTokenPattern visitKey_value_numeric(
        TRexParser.Key_value_numericContext ctx) {
      this.ctx = ctx;
      this.opCtx = ctx.numeric_op();
      return SequenceBoundaryPattern.CARET;
    }
  }

  /**
   * A dummy mock context for a numeric op patter. This is used in
   * {@link #keyValueNumericSpecialCases()} to test the corner cases of
   * the visitor.
   */
  private static class DummyNumericContext extends TRexParser.Key_value_numericContext {
    private TRexParser.Numeric_opContext opCtx;
    private String op;
    private String number;
    public DummyNumericContext(
        TRexParser.Braceless_key_value_pairContext ctx,
        TRexParser.Numeric_opContext opCtx,
        String op,
        String number) {
      super(ctx);
      this.op = op;
      this.opCtx = opCtx;
      this.number = number;
    }
    @Override public TerminalNode UnquotedStringLiteral() {
      return new TerminalNodeImpl(null) {
        @Override public String getText() {
          return "key";
        }
      };
    }
    @Override public TerminalNode Number() {
      return new TerminalNodeImpl(null) {
        @Override public String getText() {
          return number;
        }
      };
    }
    @Override public TRexParser.Numeric_opContext numeric_op() {
      return new TRexParser.Numeric_opContext(opCtx, 0) {
        @Override public String getText() {
          return op;
        }
      };
    }
  }

  /**
   * Test the corner cases for the numeric key value visitor.
   */
  @TestFactory Iterable<DynamicTest> keyValueNumericSpecialCases() {
    // This is an elaborate way to get a valid parser context
    TRexLexer lexer = new TRexLexer(CharStreams.fromString("key>42"));
    lexer.removeErrorListeners();
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    TRexParser parser = new TRexParser(tokens);
    parser.removeErrorListeners();
    TRexParser.Braceless_key_value_pairContext eval = parser.braceless_key_value_pair();
    RecordingNumericVisitor metadata = new RecordingNumericVisitor();
    metadata.visit(eval);

    return Arrays.asList(
        DynamicTest.dynamicTest("should not parse invalid numbers", () -> {
          DummyNumericContext ctx
              = new DummyNumericContext(metadata.ctx, metadata.opCtx, "<", "nan");
          SingleTokenPatternVisitor visitor = new SingleTokenPatternVisitor(
              "original_pattern", null, false, "default");
          assertThrows(PatternSyntaxException.class,
              () -> visitor.visitKey_value_numeric(ctx));
        }),
        DynamicTest.dynamicTest("should not parse invalid operators", () -> {
          DummyNumericContext ctx
              = new DummyNumericContext(metadata.ctx, metadata.opCtx, "??", "10");
          SingleTokenPatternVisitor visitor = new SingleTokenPatternVisitor(
              "original_pattern", null, false, "default");
          assertThrows(PatternSyntaxException.class,
              () -> visitor.visitKey_value_numeric(ctx));

        })
    );
  }

  // endregion: compile pattern

  /**
   * Test the value rule on some real cases.
   */
  @TestFactory Iterable<DynamicTest> value() {
    return Arrays.asList(
        matches("hello", "hello"),
        matches("/hello/", "hello"),
        matches("/你好/", "你好"),
        matches("/你./", "你好"),
        matches("/hell./", "hello"),
        matches("/h.*/", "hello"),
        matches("\"hello\"", "hello"),
        matches("42", "42"),
        // Weird case, but the double negative should be resolved
        matches("--42", "42"),
        // We should parse special characters
        matches(",", ","),
        matches("[{word:,}]", ","),
        matches("-LCB-", "{"),
        matches("-RCB-", "}"),
        matches(":", ":"),
        matches("hello: world", "hello: world"),
        matches("hello : world", "hello: world"),
        // We should parse escaped regexes
        matches("/\\+/", "+"),

        notMatches("hello", "world"),
        notMatches("\"hello\"", "world"),
        notMatches("42", "12"),
        notMatches("/foo/", "hello")
    );
  }

  /**
   * Test looking up a simple key value pair.
   */
  @TestFactory Iterable<DynamicTest> keyValuePair() {
    return Arrays.asList(
        matches("[{word:hello}]", "hello"),
        matches("[word:hello]", "hello"),
        matches("[{text:hello}]", "hello"),
        matches("[{word:\"-RRB-\"}]", ")"),
        matches("[{word:-RRB-}]", ")"),
        // Look up a key that's nonstandard
        matches("[{CharacterOffsetBeginAnnotation:0}]", "hello"),
        // A weird case: treat -0 as 0
        matches("[{CharacterOffsetBeginAnnotation:-0}]", "hello"),
        matches("[{text:\")\"}]", ")"),

        // Numeric operators
        // note[gabor] IndexAnnotation is 1-indexed in CoreNLP
        matches("[{IndexAnnotation == 1}]", "hello"),
        matches("[{IndexAnnotation = 1}]", "hello"),
        matches("[{IndexAnnotation != 2}]", "hello"),
        matches("[{IndexAnnotation >= 1}]", "hello"),
        matches("[{IndexAnnotation > 0}]", "hello"),
        matches("[{IndexAnnotation > -1}]", "hello"),
        matches("[{IndexAnnotation < 2}]", "hello"),
        matches("[{IndexAnnotation <= 2}]", "hello"),
        matches("[IndexAnnotation == 1]", "hello"),
        notMatches("[{IndexAnnotation != 1}]", "hello"),
        notMatches("[{IndexAnnotation < 1}]", "hello world"),
        notMatches("[] [{IndexAnnotation < 2}]", "hello world"),

        // Null checks
        matches("[{!lemma}]", "hello"),
        matches("[!lemma]", "hello"),

        // Simple cases of not matching
        notMatches("[{missingkey:foo}]", "hello"),
        notMatches("[{word:/foo/}]", "hello"),

        // Behavior without braces
        notMatches("word:hello", "hello"),
        matches("word:hello", "word : hello")
    );
  }

  /**
   * Test the wildcard token.
   */
  @TestFactory Iterable<DynamicTest> wildcard() {
    return Arrays.asList(
        matches("[]", "hello"),
        matches("[]", "42")
    );
  }

  /**
   * Test conjunction, disjunction, and negation within a single token.
   */
  @TestFactory Iterable<DynamicTest> singleTokenKeywords() {
    return Arrays.asList(
        matches(":", ":"),
        matches("<", "<"),
        matches(">", ">"),
        matches("==", "=="),
        matches("!", "!")
        // note[gabor]: the following do not tokenize into a single token
        //matches("<=", "<="),
        //matches(">=", ">="),
        //matches("!=", "!="),
    );
  }

  /**
   * Test conjunction, disjunction, and negation within a single token.
   */
  @TestFactory Iterable<DynamicTest> singleTokenLogicSingleOperator() {
    return Arrays.asList(
        // Single connective
        matches("[{word:hello} | {word:world}]", "hello"),
        matches("[{word:world} | {word:hello}]", "hello"),
        matches("[{word:hello} | {word:hello}]", "hello"),
        matches("[{word:hello} & {text:hello}]", "hello"),
        matches("[word:hello & {text:hello}]", "hello"),
        matches("[word:hello & text:hello]", "hello"),
        matches("[!{word:foo}]", "hello"),
        matches("[!word:foo]", "hello"),  // strange, but valid

        notMatches("[{word:foo} | {word:bar}]", "hello"),
        notMatches("[{word:foo} & {word:bar}]", "hello"),
        notMatches("[{word:hello} & {word:bar}]", "hello"),
        notMatches("[{word:foo} & {word:hello}]", "hello"),
        notMatches("[!{word:hello}]", "hello")
    );
  }

  /**
   * Test conjunction, disjunction, and negation within a single token,
   * tying together multiple operators
   */
  @TestFactory Iterable<DynamicTest> singleTokenLogicMultiOperator() {
    return Arrays.asList(
        matches("[{word:be} | !{word:be}]", "question"),
        matches("[!{word:be} | {word:be}]", "hello"),
        matches("[!{missingkey:be} | {missingkey:be}]", "hello"),
        matches("[{word:hello} & {text:hello} & !{word:foo}]", "hello"),
        matches("[word:hello & text:hello & !{word:foo}]", "hello"),

        // false | true & false -> (false | true) & false -> false
        notMatches("[{word:foo} | {word:hello} & {word:bar}]", "hello")
    );
  }

  /**
   * Test matching a sequence of tokens, without any special
   * repeat semantics.
   */
  @TestFactory Iterable<DynamicTest> tokenSequenceSimple() {
    return Arrays.asList(
        matches("hello world", "hello world"),
        matches("hello [{word:world} | {word:mom}]", "hello world"),
        matches("[] []", "hello world"),
        matches("hello []", "hello world"),
        matches("[] world", "hello world"),

        notMatches("hello world", "hello"),
        notMatches("hello world", "world"),
        notMatches("hello", "hello world"),
        notMatches("[]", "hello world"),
        notMatches("[] []", "hello")
    );
  }

  /**
   * Test the various repeat operators, without requiring complex
   * backtracking. If you add tests here, it's also recommended
   * to add tests to {@link #tokenSequenceRepeatSimpleReluctant()}.
   */
  @TestFactory Iterable<DynamicTest> tokenSequenceRepeatSimple() {
    return Arrays.asList(
        matches("a+", "a"),
        matches("a+", "a a"),
        matches("a+", "a a a a a a a a a a"),
        matches("hello a*", "hello"),
        matches("hello a*", "hello a"),
        matches("hello a*", "hello a a"),
        matches("hello a*", "hello a a a a a a a a a a"),
        matches("a* b", "b"),
        matches("/[ab]/+", "a b a b a"),
        matches("h i{0,3}", "h"),
        matches("h i{0,3}", "h i"),
        matches("h i{0,3}", "h i i i"),
        matches("h e{0,3} y", "h y"),
        matches("h e{0,3} y", "h e y"),
        matches("h e{0,3} y", "h e e e y"),
        matches("h e{1} y", "h e y"),
        matches("h e{1,} y", "h e y"),
        matches("h e{1,} y", "h e e e y"),
        matches("a b?", "a"),
        matches("a b?", "a b"),

        notMatches("hello a+", "hello"),
        notMatches("a+ b", "a a a"),
        notMatches("a* b", "a a a"),
        notMatches("h i{0,3}", "h i i i i"),
        notMatches("h i{1,3}", "h"),
        notMatches("h e{1} y", "h y"),
        notMatches("h e{1} y", "h e e y"),
        notMatches("h e{1,} y", "h y"),
        notMatches("h e{0,3} y", "h e e e e y"),
        notMatches("h e{1,3} y", "h y"),
        notMatches("h e{2,3} y", "h e y"),
        notMatches("a b?", "a b b")
    );
  }

  /**
   * Test the various repeat operators, without requiring complex
   * backtracking. This tests the reluctant operators, and should
   * mirror the tests in {@link #tokenSequenceRepeatSimple()}.
   */
  @TestFactory Iterable<DynamicTest> tokenSequenceRepeatSimpleReluctant() {
    return Arrays.asList(
        matches("a+?", "a"),
        matches("a+?", "a a"),
        matches("a+?", "a a a a a a a a a a"),
        matches("hello a*?", "hello"),
        matches("hello a*?", "hello a"),
        matches("hello a*?", "hello a a"),
        matches("hello a*?", "hello a a a a a a a a a a"),
        matches("a*? b", "b"),
        matches("/[ab]/+?", "a b a b a"),
        matches("h i{0,3}?", "h"),
        matches("h i{0,3}?", "h i"),
        matches("h i{0,3}?", "h i i i"),
        matches("h e{0,3}? y", "h y"),
        matches("h e{0,3}? y", "h e y"),
        matches("h e{0,3}? y", "h e e e y"),
        matches("h e{1,}? y", "h e y"),
        matches("h e{1,}? y", "h e e e y"),
        matches("a b??", "a"),
        matches("a b??", "a b"),

        notMatches("hello a+?", "hello"),
        notMatches("a+? b", "a a a"),
        notMatches("a*? b", "a a a"),
        notMatches("h i{0,3}?", "h i i i i"),
        notMatches("h i{1,3}?", "h"),
        notMatches("h e{1}? y", "h y"),
        notMatches("h e{1}? y", "h e e y"),
        notMatches("h e{1,}? y", "h y"),
        notMatches("h e{0,3}? y", "h e e e e y"),
        notMatches("h e{1,3}? y", "h y"),
        notMatches("h e{2,3}? y", "h e y"),
        notMatches("a b??", "a b b")
    );
  }

  /**
   * The construct a{n,m}? is ambiguous between being a reluctant match
   * or being an optional match on a{n,m}. This test ensures that we're treating
   * it as the later. Similarly for a{n,}? and a{n}?
   */
  @TestFactory Iterable<DynamicTest> reluctantQmarkAmbiguity() {
    return Arrays.asList(
        // a{n,m}?
        matches("s a{2,3}?", "s a a"),
        matches("s a{2,3}?", "s a a a"),
        notMatches("s a{2,3}?", "s a"),
        notMatches("s a{2,3}?", "s a a a a"),
        notMatches("s a{2,3}?", "s"),
        // a{n,}?
        matches("s a{2,}?", "s a a"),
        matches("s a{2,}?", "s a a a"),
        matches("s a{2,}?", "s a a a a"),
        notMatches("s a{2,}?", "s a"),
        notMatches("s a{2,}?", "s"),
        // a{n}?
        matches("s a{2}?", "s a a"),
        notMatches("s a{2}?", "s a"),
        notMatches("s a{2}?", "s a a a"),
        notMatches("s a{2}?", "s")
    );
  }

  /**
   * A simple test to ensure that the plus operator's {@link Pattern#consume(List, int, Matcher)}
   * iterators return multiple matches, rather than just the greediest match
   */
  @Test void plusIteratorHasMultipleMatches() throws ClassCastException {
    SequencePattern pattern = (SequencePattern) Pattern.compile("a+");
    List<TRexInputToken> tokens = NLPUtils.tokenize("a a a").get(0)
        .stream()
        .map(CoreLabelInputToken::new)
        .collect(Collectors.toList());
    PrimitiveIterator.OfInt iter
        = pattern.consume(tokens, 0, new Matcher<>(pattern, tokens));

    assertTrue(iter.hasNext(), "We should match the pattern");
    assertEquals(3, iter.nextInt());
    assertTrue(iter.hasNext(), "We should have more than one match");
    assertEquals(2, iter.nextInt(), "Our second match should be 2");
    assertTrue(iter.hasNext());
    assertEquals(1, iter.nextInt());
  }

  /**
   * A simple test to ensure that the plus operator's {@link Pattern#consume(List, int, Matcher)}
   * iterators return multiple matches even when it's a reluctant quantifier.
   * This is the reluctant mirror to {@link #plusIteratorHasMultipleMatches()}.
   */
  @Test void plusIteratorHasMultipleMatchesReluctant() throws ClassCastException {
    SequencePattern pattern = (SequencePattern) Pattern.compile("a+?");
    List<TRexInputToken> tokens = NLPUtils.tokenize("a a a").get(0)
        .stream()
        .map(CoreLabelInputToken::new)
        .collect(Collectors.toList());
    PrimitiveIterator.OfInt iter
        = pattern.consume(tokens, 0, new Matcher<>(pattern, tokens));

    assertTrue(iter.hasNext(), "We should match the pattern");
    assertEquals(1, iter.nextInt());
    assertTrue(iter.hasNext(), "We should have more than one match");
    assertEquals(2, iter.nextInt(), "Our second match should be 2");
    assertTrue(iter.hasNext());
    assertEquals(3, iter.nextInt());
  }

  /**
   * A simple test to ensure that the star operator's {@link Pattern#consume(List, int, Matcher)}
   * iterators return multiple matches, rather than just the greediest match
   */
  @Test void starIteratorHasMultipleMatches() throws ClassCastException {
    SequencePattern pattern = (SequencePattern) Pattern.compile("a*");
    List<TRexInputToken> tokens = NLPUtils.tokenize("a a").get(0)
        .stream()
        .map(CoreLabelInputToken::new)
        .collect(Collectors.toList());
    PrimitiveIterator.OfInt iter
        = pattern.consume(tokens, 0, new Matcher<>(pattern, tokens));

    assertTrue(iter.hasNext(), "We should match the pattern");
    assertEquals(2, iter.nextInt());
    assertTrue(iter.hasNext(), "We should have more than one match");
    assertEquals(1, iter.nextInt(), "Our second match should be 2");
    assertTrue(iter.hasNext());
    assertEquals(0, iter.nextInt());
  }

  /**
   * A simple test to ensure that the star operator's {@link Pattern#consume(List, int, Matcher)}
   * iterators return multiple matches, even for the reluctant version.
   * This is the reluctant mirror of {@link #starIteratorHasMultipleMatches()}.
   */
  @Test void starIteratorHasMultipleMatchesReluctant() throws ClassCastException {
    SequencePattern pattern = (SequencePattern) Pattern.compile("a*?");
    List<TRexInputToken> tokens = NLPUtils.tokenize("a a").get(0)
        .stream()
        .map(CoreLabelInputToken::new)
        .collect(Collectors.toList());
    PrimitiveIterator.OfInt iter
        = pattern.consume(tokens, 0, new Matcher<>(pattern, tokens));

    assertTrue(iter.hasNext(), "We should match the pattern");
    assertEquals(0, iter.nextInt());
    assertTrue(iter.hasNext(), "We should have more than one match");
    assertEquals(1, iter.nextInt(), "Our second match should be 2");
    assertTrue(iter.hasNext());
    assertEquals(2, iter.nextInt());
  }

  /**
   * Test that we backtrack correctly.
   * If you add tests here, it's also recommended to add tests to
   * {@link #tokenSequenceRepeatBacktrackReluctant()}.
   */
  @TestFactory Iterable<DynamicTest> tokenSequenceRepeatBacktrack() {
    return Arrays.asList(
        matches("/[ab]/+ b", "a b"),
        matches("/[ab]/* b", "a b"),
        matches("/[ab]/* b", "b"),
        matches("(a+ b)* b", "a b a a b a b b"),
        matches("(a b+)*", "a b"),
        matches("(a b+)*", "a b a b a b b"),
        // note[gabor] this forces backtracking on the b+
        matches("(a b+)* b a", "a b a b b a"),
        matches("a{0,1} (a b+)*", "a b"),
        matches("a{0,1} (a b+)*", "a b b"),
        matches("a{0,1} (a b+)*", "a b a b a b b"),
        matches("a{0,1} /[ab]/ b", "a b"),
        matches("a? (a b+)*", "a b"),
        matches("a* /[ab]/ b", "a b"),
        matches("(a+ b)* (a b+)*", "a b a b a b b"),
        matches("(a+ b)* (a b+)*", "a b a b a b b b a b")
    );
  }

  /**
   * Test that we backtrack correctly for reluctant quantifiers.
   * This is the reluctant mirror of {@link #tokenSequenceRepeatBacktrack()}
   */
  @TestFactory Iterable<DynamicTest> tokenSequenceRepeatBacktrackReluctant() {
    return Arrays.asList(
        matches("/[ab]/+? b", "a b"),
        matches("/[ab]/*? b", "a b"),
        matches("/[ab]/*? b", "b"),
        matches("(a+ b)*? b", "a b a a b a b b"),
        matches("(a b+)*?", "a b"),
        matches("(a b+)*?", "a b a b a b b"),
        matches("(a b+?)*", "a b a b a b b"),
        matches("(a b+?)*?", "a b a b a b b"),
        matches("a{0,1} (a b+)*?", "a b"),
        matches("a{0,1} (a b+)*?", "a b b"),
        matches("a{0,1} (a b+)*?", "a b a b a b b"),
        matches("a{0,1}? (a b+)*?", "a b a b a b b"),
        matches("a{0,1}? (a b+?)*?", "a b a b a b b"),
        matches("a{0,1}? (a b+?)*", "a b a b a b b"),
        matches("a{0,1}? /[ab]/ b", "a b"),
        matches("a?? (a b+)*", "a b"),
        matches("a?? (a b+?)*", "a b"),
        matches("a?? (a b+?)*?", "a b"),
        matches("a*? /[ab]/ b", "a b"),
        matches("(a+? b)* (a b+)*", "a b a b a b b"),
        matches("(a+? b)*? (a b+)*", "a b a b a b b"),
        matches("(a+? b)*? (a b+?)*", "a b a b a b b"),
        matches("(a+? b)*? (a b+?)*?", "a b a b a b b"),
        matches("(a+ b)* (a b+)*?", "a b a b a b b"),
        matches("(a+ b)*? (a b+)*?", "a b a b a b b"),
        matches("(a+? b)* (a b+?)*", "a b a b a b b b a b"),
        matches("a*? a", "a a a a a"),
        matches("a? (a b+)*?", "a b b b"),
        matches("(a+){2,}?", "a a a a a a"),
        matches("a? (a? b{2,3}?)*?", "a a b b b b b a b b b"),

        notMatches("a{1,2}?", "a a a a")
    );
  }

  /**
   * Test that conjunctions behave as they should
   */
  @TestFactory Iterable<DynamicTest> conjunction() {
    return Arrays.asList(
        matches("a & a", "a"),
        matches("[{word:a}] & [{text:a}]", "a"),
        matches("[{word:-LRB-}] & [{text:\"(\"}]", "("),
        matches("a b & a b", "a b"),
        matches("(a b+ & a b) b", "a b b"),
        matches("(a b+ & a b+) b", "a b b"),
        matches("(a b+ /[bc]/+ & a b+ /[bc]/+) b", "a b b c b"),
        matches("a b & /a|b/+", "a b"),

        notMatches("a & b", "a"),
        notMatches("a & b", "b"),
        notMatches("a & b", "c"),
        notMatches("a b & a", "a b"),
        notMatches("(a b) & a", "a b"),
        notMatches("a a & a", "a a"),
        notMatches("a b & c", "a b")
    );
  }

  /**
   * Test behavior when the conjunction iterator is empty.
   */
  @Test void conjunctionIterator() throws ClassCastException {
    ConjunctionPattern p = (ConjunctionPattern) Pattern.compile("a b+ /[bc]/+ & a b+");
    List<CoreLabel> tokens = NLPUtils.tokenize("a b b c").get(0);
    List<CoreLabelInputToken> input
        = tokens.stream().map(CoreLabelInputToken::new).collect(Collectors.toList());
    ConjunctionIterator iter =
        (ConjunctionIterator) p.consume(input, 0, new Matcher<>(p, input));
    assertTrue(iter.hasNext(), "we should match on 'a b b'");
    assertEquals(3, iter.nextInt());
    assertFalse(iter.hasNext(), "we should not have another match");
    assertThrows(NoSuchElementException.class, iter::nextInt);
  }

  /**
   * Test that disjunctions behave as they should
   */
  @TestFactory Iterable<DynamicTest> disjunction() {
    return Arrays.asList(
        matches("a | b", "a"),
        matches("a | b", "b"),
        matches("a b | x y", "a b"),
        matches("a b | x y", "x y"),
        matches("a b c | x y", "a b c"),
        matches("a b c | x y", "x y"),
        matches("(a b)+ | (a b)+ c", "a b a b"),
        matches("(a b)+ | (a b)+ c", "a b a b c"),
        matches("(a b)+? | (a b)+? c", "a b a b"),
        matches("(a b)+? | (a b)+? c", "a b a b c"),
        matches("a | b | c", "a"),
        matches("a | b | c", "b"),
        matches("a | b | c", "c"),
        matches("(a | b) | c", "b"),
        matches("a | (b | c)", "b"),
        matches("(I | I am | me) am good", "I am good"),
        matches("a+ | a+", "a a a"),
        // The following tests the caching of already returned indices in disjunctions
        matches("(a{2,3} | a+) a a", "a a a"),

        notMatches("a | b", "c")
    );
  }

  /**
   * Test behavior when the disjunction iterator is empty.
   */
  @Test void disjunctionIterator() throws ClassCastException {
    DisjunctionPattern p = (DisjunctionPattern) Pattern.compile("x | (a b)");
    List<CoreLabel> tokens = NLPUtils.tokenize("a b").get(0);
    List<CoreLabelInputToken> input
        = tokens.stream().map(CoreLabelInputToken::new).collect(Collectors.toList());
    DisjunctionIterator iter =
        (DisjunctionIterator) p.consume(input, 0, new Matcher<>(p, input));
    assertTrue(iter.hasNext(), "we should match on 'a b b'");
    assertEquals(2, iter.nextInt());
    assertFalse(iter.hasNext(), "we should not have another match");
    assertThrows(NoSuchElementException.class, iter::nextInt);
  }


  /**
   * Test the sequence boundary tokens ^ and $.
   */
  @TestFactory Iterable<DynamicTest> sequenceBoundary() {
    return Arrays.asList(
        matches("^ a", "a"),
        matches("(^ | a) b", "b"),
        matches("(^ | a) b", "a b"),
        matches("a $", "a"),
        matches("a (b | $)", "a"),
        matches("a (b | $)", "a b"),

        notMatches("^ a b", "a a b"),
        notMatches("a ^ b", "a b"),
        notMatches("a b $", "a b b"),
        notMatches("a $ b", "a b")
    );
  }

  /**
   * Test that we evaluate comments correctly.
   * These should really be stripped out at the lexer level, so this
   * is just a sanity check
   */
  @TestFactory Iterable<DynamicTest> comments() {
    return Arrays.asList(
        matches("a b // comment", "a b"),
        matches("a b //", "a b"),
        matches("a /b*/", "a bb"),
        matches("a /b*///", "a bb"),
        matches("a /* comment */ b c", "a b c")
    );
  }

  /**
   * Test a regression on a simplified IAMGOOD smalltalk pattern, which uses a
   * variable lookup
   */
  @Test void variableLookupIAmGood() {
    Map<String, Pattern> variables = Collections.singletonMap("I",
        Pattern.compile("i | (i \"'m\") | im | imk | (i \"'d\") | id | (i \"'ve\")", false));
    List<CoreLabel> tokens = NLPUtils.tokenize("I am good").get(0);
    Pattern compiledPattern = Pattern.compile("$I am good", false, variables);
    assertTrue(CoreNLPUtils.matcher(compiledPattern, tokens).matches());
  }

  /**
   * Test that we are robust to case sensitivity, including with unicode
   * casing if applicable.
   */
  @TestFactory Iterable<DynamicTest> caseSensitivity() {
    return Arrays.asList(
        matches("hello", "Hello", false),
        notMatches("hello", "Hello", true),
        // Regexes must specify their own case sensitivity
        notMatches("/hell./", "Hello", false),
        notMatches("/hell./", "Hello", true),
        matches("resumé", "RESUMÉ", false),
        notMatches("resumé", "RESUMÉ", true),
        // Regexes must specify their own case sensitivity
        notMatches("/r.sumé/", "RESUMÉ", false),
        notMatches("/r.sumé/", "RESUMÉ", true)
    );
  }

  // region: find

  /**
   * Test the {@link Matcher#find()} method for returning multiple
   * matches on a single input.
   */
  @Test void testFindSimple() {
    List<CoreLabel> tokens = NLPUtils.tokenize("a a a").get(0);
    Pattern compiledPattern = Pattern.compile("a", false);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(compiledPattern, tokens);

    assertTrue(matcher.find());
    assertEquals(0, matcher.start());
    assertEquals(1, matcher.end());

    assertTrue(matcher.find());
    assertEquals(1, matcher.start());
    assertEquals(2, matcher.end());

    assertTrue(matcher.find());
    assertEquals(2, matcher.start());
    assertEquals(3, matcher.end());

    assertFalse(matcher.find());
  }

  /**
   * Test the {@link Matcher#find()} method for returning multiple
   * matches from a given index
   */
  @Test void testFindExponentialPattern() {
    List<CoreLabel> tokens = NLPUtils.tokenize("a a a").get(0);
    Pattern compiledPattern = Pattern.compile("a+", false);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(compiledPattern, tokens);

    // Match 3 spans from index 0
    assertTrue(matcher.find());
    assertEquals(0, matcher.start());
    assertEquals(3, matcher.end());
    assertTrue(matcher.find());
    assertEquals(0, matcher.start());
    assertEquals(2, matcher.end());
    assertTrue(matcher.find());
    assertEquals(0, matcher.start());
    assertEquals(1, matcher.end());

    // Match 2 spans from index 1
    assertTrue(matcher.find());
    assertEquals(1, matcher.start());
    assertEquals(3, matcher.end());
    assertTrue(matcher.find());
    assertEquals(1, matcher.start());
    assertEquals(2, matcher.end());

    // Match 1 spans from index 2
    assertTrue(matcher.find());
    assertEquals(2, matcher.start());
    assertEquals(3, matcher.end());

    assertFalse(matcher.find());
  }

  /**
   * Test the {@link Matcher#find()} method doesn't return duplicate
   * spans.
   */
  @Test void testFindUniqueResults() {
    List<CoreLabel> tokens = NLPUtils.tokenize("a").get(0);
    Pattern compiledPattern = Pattern.compile("a | b", false);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(compiledPattern, tokens);
    assertTrue(matcher.find());
    assertEquals(0, matcher.start());
    assertEquals(1, matcher.end());
    assertFalse(matcher.find());
  }

  /**
   * Test the {@link Matcher#find()} method for reluctant quantifiers.
   */
  @Test void testFindReluctant() {
    List<CoreLabel> tokens = NLPUtils.tokenize("a a a").get(0);
    Pattern compiledPattern = Pattern.compile("a+?", false);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(compiledPattern, tokens);

    assertTrue(matcher.find());
    assertEquals(0, matcher.start());
    assertEquals(1, matcher.end());

    assertTrue(matcher.find());
    assertEquals(0, matcher.start());
    assertEquals(2, matcher.end());

    assertTrue(matcher.find());
    assertEquals(0, matcher.start());
    assertEquals(3, matcher.end());

    assertTrue(matcher.find());
    assertEquals(1, matcher.start());
    assertEquals(2, matcher.end());

    assertTrue(matcher.find());
    assertEquals(1, matcher.start());
    assertEquals(3, matcher.end());

    assertTrue(matcher.find());
    assertEquals(2, matcher.start());
    assertEquals(3, matcher.end());

    assertFalse(matcher.find());
  }

  // endregion: find

  /**
   * Test that the timeout is actually respected.
   */
  @Test void testTimeout() {
    // Construct an absurdly adversarial rule
    List<CoreLabel> tokens = NLPUtils.tokenize(
        "a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a").get(0);
    Pattern compiledPattern = Pattern.compile("(((((a*)*)*)*)*)*", false);

    // Try to match with a timeout
    log.info("Starting matcher");
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(compiledPattern, tokens);
    assertThrows(RuntimeException.class,
        () -> matcher.matches(Duration.ofMillis(50)));
  }

  /**
   * Test that the timeout is actually respected, where the core pattern
   * is itself a more complex pattern.
   */
  @Test void testTimeoutComplexBranch() {
    // Construct an absurdly adversarial rule
    List<CoreLabel> tokens = NLPUtils.tokenize(
        "a a a a a a a a a a a a a a a a a a a a a a a a a a a a a a").get(0);

    for (String pattern : Arrays.asList(
        // greedy quantifiers
        "(((((a a)*)*)*)*)*",
        // reluctant quantifiers
        "(((((a a)*?)*?)*?)*?)*?"
    )) {
      Pattern compiledPattern = Pattern.compile(pattern, false);

      // Try to match with a timeout
      log.info("Starting matcher");
      Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(compiledPattern, tokens);
      assertThrows(RuntimeException.class,
          () -> matcher.matches(Duration.ofMillis(50)));
    }
  }

  /**
   * Test that we can run matchers against a pattern in parallel
   * without causing race conditions or other errors.
   * For example, this test fails if Matcher#transientIterator is made static.
   */
  @Test void testParallelExecution() throws InterruptedException {
    // Initialize our data
    Pattern compiledPattern = Pattern.compile(
        "(to [{word:be} & {text:be}] (or|not)*)+ ,? that is (the question & [] [])",
        false);
    List<List<CoreLabel>> sentences = Arrays.asList(
        NLPUtils.tokenize("to be or not to be, that is the question").get(0),
        NLPUtils.tokenize("to be to be or not to be, that is the question").get(0),
        NLPUtils.tokenize("to be or not to be, that is the answer").get(0)
    );

    // Start a bunch of concurrent pattern matches
    AtomicBoolean seenError = new AtomicBoolean(false);
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < 10; ++i) {
      Thread t = new Thread(() -> {
        for (int pass = 0; pass < 100; ++pass) {
          if (!CoreNLPUtils.matcher(compiledPattern, sentences.get(0)).matches()) {
            seenError.set(true);
          }
          if (!CoreNLPUtils.matcher(compiledPattern, sentences.get(1)).matches()) {
            seenError.set(true);
          }
          if (CoreNLPUtils.matcher(compiledPattern, sentences.get(2)).matches()) {
            seenError.set(true);
          }
        }
      });
      t.setDaemon(true);
      t.start();
      threads.add(t);
    }

    // Wait for everything to finish
    for (Thread t : threads) {
      t.join();
    }

    // Check that we matched everything correctly
    assertFalse(seenError.get(), "We should not have failed any of our pattern matches");
  }

  // region: capture groups

  /**
   * A simple test for getting an anonymous capture group
   */
  @Test void testAnonymousCaptureGroupSimple() {
    Pattern pattern = Pattern.compile("(a b) c", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b c").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.matches());
    Matcher.CaptureGroup<CoreLabelInputToken> fullGroup = matcher.group(0);
    assertNotNull(fullGroup);
    assertEquals(0, fullGroup.getBeginInclusive());
    assertEquals(3, fullGroup.getEndExclusive());
    assertEquals(3, fullGroup.matchedTokens().size());
    assertEquals("a", fullGroup.matchedTokens().get(0).token.word());
    assertEquals("b", fullGroup.matchedTokens().get(1).token.word());
    assertEquals("c", fullGroup.matchedTokens().get(2).token.word());

    Matcher.CaptureGroup<CoreLabelInputToken> anonymous = matcher.group(1);
    assertNotNull(anonymous);
    assertEquals(0, anonymous.getBeginInclusive());
    assertEquals(2, anonymous.getEndExclusive());
    assertEquals(2, anonymous.matchedTokens().size());
    assertEquals("a", anonymous.matchedTokens().get(0).token.word());
    assertEquals("b", anonymous.matchedTokens().get(1).token.word());
  }

  /**
   * Test that we get the correct indices even if we're not starting
   * at the origin.
   */
  @Test void testAnonymousCaptureGroupOffset() {
    Pattern pattern = Pattern.compile("a (b c)", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b c").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.matches());
    Matcher.CaptureGroup<CoreLabelInputToken> anonymous = matcher.group(1);
    assertNotNull(anonymous);
    assertEquals(1, anonymous.getBeginInclusive());
    assertEquals(3, anonymous.getEndExclusive());
  }

  /**
   * Test that we get the correct indices even if we had to backtrack
   * at some point during our match.
   */
  @Test void testAnonymousCaptureGroupBacktracking() {
    Pattern pattern = Pattern.compile("(a b*) b b", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b b b b").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.matches());
    Matcher.CaptureGroup<CoreLabelInputToken> anonymous = matcher.group(1);
    assertNotNull(anonymous);
    assertEquals(0, anonymous.getBeginInclusive());
    assertEquals(3, anonymous.getEndExclusive());
  }

  /**
   * We should not be able to get a group that doesn't exist
   */
  @Test void testAnonymousCaptureGroupOutOfBounds() {
    Pattern pattern = Pattern.compile("(a b) c", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b c").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);
    assertThrows(IllegalStateException.class, () -> matcher.group(-1));
    assertThrows(IllegalStateException.class, () -> matcher.group(2));
    assertTrue(matcher.matches());
    assertThrows(IndexOutOfBoundsException.class, () -> matcher.group(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> matcher.group(2));
  }

  /**
   * If we have a pattern that happens to not match one of the groups,
   * we should return null on it.
   */
  @Test void testAnonymousCaptureGroupNoMatch() {
    Pattern pattern = Pattern.compile("(a b) | (c d)", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.matches());
    assertNotNull(matcher.group(0), "We should always have a full match");
    assertNotNull(matcher.group(1));
    assertNull(matcher.group(2));
  }

  /**
   * A simple test for getting an anonymous capture group
   */
  @Test void testNamedCaptureGroupSimple() {
    Pattern pattern = Pattern.compile("(?<name> a b) c", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b c").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.matches());
    Matcher.CaptureGroup<CoreLabelInputToken> fullGroup = matcher.group("name");
    assertNotNull(fullGroup);
    assertEquals(0, fullGroup.getBeginInclusive());
    assertEquals(2, fullGroup.getEndExclusive());
    assertEquals(2, fullGroup.matchedTokens().size());
    assertEquals("a", fullGroup.matchedTokens().get(0).token.word());
    assertEquals("b", fullGroup.matchedTokens().get(1).token.word());
  }

  /**
   * Inspired by a regression from the temporal constraint extractor: capture
   * a repeated element in a named capture group. This makes sure that the capture group
   * span is correct, even if there's another match with a smaller capture group afterwards.
   *
   * The root cause of the bug is a subtle case in SequencePattern#primeBranchEager, where
   * we call hasNext twice in a row. If the second call returns true, we may inadvertently
   * overwrite a capture group. In this test case, the sequence pattern for (b (c+)) has
   * multiple matches. By peeking at the second match in c+, the capture group for c+ gets
   * overwritten.
   */
  @Test void testNamedCaptureWildcard() {
    Pattern pattern = Pattern.compile("a (b (?<name> c+))", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b c c").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    // On the first find, we return the full match
    assertTrue(matcher.find());
    Matcher.CaptureGroup<CoreLabelInputToken> group = matcher.group("name");
    assertNotNull(group);
    assertEquals(2, group.getBeginInclusive());
    assertEquals(4, group.getEndExclusive());
    assertEquals(2, group.matchedTokens().size());

    // On the next find, we return a smaller group match
    assertTrue(matcher.find());
    group = matcher.group("name");
    assertNotNull(group);
    assertEquals(2, group.getBeginInclusive());
    assertEquals(3, group.getEndExclusive());
    assertEquals(1, group.matchedTokens().size());
  }

  /**
   * If we have a pattern that happens to not match one of the groups,
   * we should return null on it.
   */
  @Test void testNamedCaptureGroupNoMatch() {
    Pattern pattern = Pattern.compile("(?<one> a b) | (?<two> c d)", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.matches());
    assertNotNull(matcher.group(0), "We should always have a full match");
    assertNotNull(matcher.group("one"));
    assertNull(matcher.group("two"));
  }

  /**
   * Test what happens when our capture group is itself the whole pattern.
   * We should be able to retrieve the group by index 0, by index 1, and by
   * name.
   */
  @Test void testCaptureGroupWholePattern() {
    Pattern pattern = Pattern.compile("(?<name> a b)", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.matches());
    assertNotNull(matcher.group(0), "We should always have a full match");
    assertNotNull(matcher.group(1), "We should still have our capture group match");
    assertNotNull(matcher.group("name"), "We should be able to lookup our group by name");
    assertEquals(matcher.group(0), matcher.group(1));
    assertEquals(matcher.group(0), matcher.group("name"));
  }

  /**
   * Test that we can retrieve capture groups using find() where there
   * are multiple matching results.
   */
  @Test void testCaptureGroupWithFindMultimatch() {
    Pattern pattern = Pattern.compile("x | (?<name> x) y | x (?<name> x)");
    List<CoreLabel> input = NLPUtils.tokenize("x y").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    // The first match
    assertTrue(matcher.find());
    Matcher.CaptureGroup<CoreLabelInputToken> firstGroup = matcher.group(0);
    assertNotNull(firstGroup);
    assertEquals(0, firstGroup.getBeginInclusive());
    assertEquals(1, firstGroup.getEndExclusive());

    // The second match
    assertTrue(matcher.find());
    Matcher.CaptureGroup<CoreLabelInputToken> secondGroup = matcher.group(0);
    assertNotNull(secondGroup);
    assertEquals(0, secondGroup.getBeginInclusive());
    assertEquals(2, secondGroup.getEndExclusive());
    // Get the capture group in the second match
    Matcher.CaptureGroup<CoreLabelInputToken> secondGroupUnnamed = matcher.group(1);
    assertNotNull(secondGroupUnnamed);
    assertEquals(0, secondGroupUnnamed.getBeginInclusive());
    assertEquals(1, secondGroupUnnamed.getEndExclusive());
    // Make sure the named capture group is there too
    Matcher.CaptureGroup<CoreLabelInputToken> secondGroupNamed = matcher.group("name");
    assertEquals(secondGroupUnnamed, secondGroupNamed);

    // There are no more matches
    assertFalse(matcher.find());
  }

  /**
   * Test what happens when our capture group is itself the whole pattern, and
   * we match via find() and not matches().
   */
  @Test void testCaptureGroupWholePatternOnFind() {
    Pattern pattern = Pattern.compile("(?<name> a b)", false);
    List<CoreLabel> input = NLPUtils.tokenize("x a b").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.find());
    assertNotNull(matcher.group(0), "We should always have a full match");
    assertNotNull(matcher.group(1), "We should still have our capture group match");
    assertNotNull(matcher.group("name"), "We should be able to lookup our group by name");
    assertEquals(matcher.group(0), matcher.group(1));
    assertEquals(matcher.group(0), matcher.group("name"));
  }

  /**
   * Test that we can match our capture group even if it's the only token we're
   * matching.
   */
  @Test void testCaptureGroupOnFindSingleToken() {
    Pattern pattern = Pattern.compile("(?<year> /(19|20)[0-9]{2}/)");
    List<CoreLabel> input = NLPUtils.tokenize("January 2020").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.find());
    assertNotNull(matcher.group(0), "We should always have a full match");
    assertNotNull(matcher.group(1), "We should still have our capture group match");
    assertNotNull(matcher.group("year"), "We should be able to lookup our group by name");
    assertEquals(matcher.group(0), matcher.group(1));
    assertEquals(matcher.group(0), matcher.group("year"));
  }

  /**
   * Test what happens if we have a capture group in a variable that we
   * then compile into a pattern.
   */
  @Test void testCaptureGroupInVariable() {
    Pattern pattern = Pattern.compile(
        "$VAR (c)",
        false,
        Collections.singletonMap("VAR", Pattern.compile("(?<var> a b)", false)));
    List<CoreLabel> input = NLPUtils.tokenize("a b c").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.matches());
    assertNotNull(matcher.group(0), "We should always have a full match");

    Matcher.CaptureGroup<CoreLabelInputToken> var = matcher.group(1);
    assertNotNull(var);
    assertEquals(var, matcher.group("var"),
        "We should be able to get a group in a variable by name");
    assertEquals(0, var.getBeginInclusive());
    assertEquals(2, var.getEndExclusive());

    assertNotNull(matcher.group(2), "We should have two capture groups");
  }

  /**
   * Test that we can capture groups from the {@link Matcher#find()} method,
   * as opposed to the {@link Matcher#matches()} method.
   */
  @Test void testCaptureGroupWithFind() {
    Pattern pattern = Pattern.compile("(a) b", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b a b a b").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    // The first find
    assertTrue(matcher.find());
    Matcher.CaptureGroup<CoreLabelInputToken> fullMatch = matcher.group(0);
    assertNotNull(fullMatch, "We should always have a full match");
    assertEquals(0, fullMatch.getBeginInclusive());
    assertEquals(2, fullMatch.getEndExclusive());
    Matcher.CaptureGroup<CoreLabelInputToken> group = matcher.group(1);
    assertNotNull(group);
    assertEquals(0, group.getBeginInclusive());
    assertEquals(1, group.getEndExclusive());

    // The second find
    assertTrue(matcher.find());
    fullMatch = matcher.group(0);
    assertNotNull(fullMatch, "We should always have a full match");
    assertEquals(2, fullMatch.getBeginInclusive());
    assertEquals(4, fullMatch.getEndExclusive());
    group = matcher.group(1);
    assertNotNull(group);
    assertEquals(2, group.getBeginInclusive());
    assertEquals(3, group.getEndExclusive());
  }

  /**
   * Test that a capture group's value doesn't change on subsequent
   * calls to {@link Matcher#find()}.
   */
  @Test void testCaptureGroupWithFindImmutable() {
    Pattern pattern = Pattern.compile("(a) b", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b a b a b").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    // The first find
    assertTrue(matcher.find());
    Matcher.CaptureGroup<CoreLabelInputToken> firstMatch = matcher.group(1);
    assertNotNull(firstMatch);
    // The second find
    assertTrue(matcher.find());
    Matcher.CaptureGroup<CoreLabelInputToken> secondMatch = matcher.group(1);
    assertNotNull(secondMatch);

    // Check that the two remain distinct
    assertNotSame(firstMatch, secondMatch);
    assertNotEquals(firstMatch, secondMatch);
    assertNotEquals(firstMatch.getBeginInclusive(), secondMatch.getBeginInclusive());
    assertNotEquals(firstMatch.getEndExclusive(), secondMatch.getEndExclusive());
  }

  /**
   * Test capture groups with find on a non-trivial input.
   */
  @Test void testCaptureGroupWithFindComplexCase() {
    Pattern pattern = Pattern.compile("(?<x> a) (?<y> b)? c", false);
    List<CoreLabel> input = NLPUtils.tokenize("a b c a c").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.find());
    Matcher.CaptureGroup<CoreLabelInputToken> match1group1 = matcher.group(1);
    assertNotNull(match1group1);
    assertEquals(0, match1group1.getBeginInclusive());
    assertEquals(1, match1group1.getEndExclusive());

    Matcher.CaptureGroup<CoreLabelInputToken> match1group2 = matcher.group(2);
    assertNotNull(match1group2);
    assertEquals(1, match1group2.getBeginInclusive());
    assertEquals(2, match1group2.getEndExclusive());

    assertTrue(matcher.find());
    Matcher.CaptureGroup<CoreLabelInputToken> match2group1 = matcher.group(1);
    assertNotNull(match2group1);
    assertEquals(3, match2group1.getBeginInclusive());
    assertEquals(4, match2group1.getEndExclusive());

    Matcher.CaptureGroup<CoreLabelInputToken> match2group2 = matcher.group(2);
    assertNull(match2group2);
  }

  /**
   * Test when we have duplicate named capture matches.
   */
  @Test void testCaptureGroupDuplicateName() {
    Pattern pattern = Pattern.compile("(?<x> a)+ b", false);
    List<CoreLabel> input = NLPUtils.tokenize("a a a a b").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    assertTrue(matcher.find());
    Matcher.CaptureGroup<CoreLabelInputToken> capture = matcher.group("x");
    assertNotNull(capture);
    assertEquals(3, capture.getBeginInclusive());
    assertEquals(4, capture.getEndExclusive());
  }

  /**
   * A short utility to render a sequence of tokens as a space-separated string.
   */
  private static String renderTokens(List<CoreLabelInputToken> tokens) {
    return tokens.stream()
        .map(x -> x.token.originalText())
        .collect(Collectors.joining(" "));
  }

  /**
   * Test nesting capture groups.
   */
  @Test void testNestedCaptureGroups() {
    Pattern pattern = Pattern.compile(
        "(?<timestamp>(?<year>/[0-9]{2,4}/)-(?<month>/[0-1]?[0-9]/)-(?<day>/[0-3]?[0-9]/))",
        false);
    List<CoreLabel> input = NLPUtils.tokenize("2019 - 12 - 25").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    // Match the pattern and get the capture groups
    assertTrue(matcher.matches());
    Matcher.CaptureGroup<CoreLabelInputToken> timestamp = matcher.group("timestamp");
    assertNotNull(timestamp);
    Matcher.CaptureGroup<CoreLabelInputToken> year = matcher.group("year");
    assertNotNull(year);
    Matcher.CaptureGroup<CoreLabelInputToken> month = matcher.group("month");
    assertNotNull(month);
    Matcher.CaptureGroup<CoreLabelInputToken> day = matcher.group("day");
    assertNotNull(day);

    // Check the capture groups
    assertEquals("2019 - 12 - 25", renderTokens(timestamp.matchedTokens()));
    assertEquals("2019", renderTokens(year.matchedTokens()));
    assertEquals("12", renderTokens(month.matchedTokens()));
    assertEquals("25", renderTokens(day.matchedTokens()));
  }

  /**
   * Test string capture groups, where we extract a subspan of a token.
   */
  @Test void testStringCaptureGroups() {
    Pattern pattern = Pattern.compile(
        "/(?<year>[0-9]{2,4})(?<month>[0-9]{2})(?<day>[0-9]{2})/",
        false);
    List<CoreLabel> input = NLPUtils.tokenize("20191225").get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    // Match the pattern and get the capture groups
    assertTrue(matcher.matches());
    Map<String, String> groups = matcher.stringCaptureGroups();
    assertEquals("2019", groups.get("year"));
    assertEquals("12", groups.get("month"));
    assertEquals("25", groups.get("day"));
  }


  /**
   * Test string capture groups in a find() rather than in a matches().
   * This ensures that we retain the capture groups appropriately
   */
  @Test void testStringCaptureGroupInFind() {
    Pattern pattern = Pattern.compile(
        "/(?<year>[0-9]{2,4})(?<month>[0-9]{2})(?<day>[0-9]{2})/",
        false);
    List<CoreLabel> input = NLPUtils.tokenize("foo 20191225" /* note: longer input */).get(0);
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);

    // Match the pattern and get the capture groups
    assertTrue(matcher.find());
    Map<String, String> groups = matcher.stringCaptureGroups();
    assertEquals("2019", groups.get("year"));
    assertEquals("12", groups.get("month"));
    assertEquals("25", groups.get("day"));
  }

  // endregion: capture groups

  // region: compile variables

  /**
   * A simple test for compiling variables.
   */
  @Test void compileVariablesSimple() {
    Map<String, Pattern> compiled = Pattern.compileVariables(
        Collections.singletonMap("key", "na+ batman"),
        false);
    assertEquals(1, compiled.size());
    assertEquals("[default:\"na\"]+ [default:\"batman\"]", compiled.get("key").toString());
  }

  /**
   * Test that we strip away leading '$'s in variable keys
   */
  @Test void compileVariablesStripDollar() {
    Map<String, Pattern> compiled = Pattern.compileVariables(
        Collections.singletonMap("$key", "na+ batman"),
        false);
    assertEquals(1, compiled.size());
    assertEquals("[default:\"na\"]+ [default:\"batman\"]", compiled.get("key").toString());
  }

  /**
   * Test that variables can themselves depend on other variables.
   */
  @Test void compileVariablesTransitive() {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("depA", "hello");
    variables.put("depB", "world");
    variables.put("root", "$depA $depB");
    Map<String, Pattern> compiled = Pattern.compileVariables(variables, false);
    assertEquals(3, compiled.size());
    assertEquals("[default:\"hello\"] [default:\"world\"]", compiled.get("root").toString());
  }

  /**
   * Test that we can topologically sort the variable dependencies
   */
  @Test void compileVariablesSort() {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("depA", "hello");
    variables.put("root", "$depA $depB");  // note: this is between two dependencies
    variables.put("depB", "world");
    Map<String, Pattern> compiled = Pattern.compileVariables(variables, false);
    assertEquals(3, compiled.size());
    assertEquals("[default:\"hello\"] [default:\"world\"]", compiled.get("root").toString());
  }

  /**
   * Test that we can topologically sort the variable dependencies
   */
  @Test void compileVariablesCircular() {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("depA", "$depB");
    variables.put("depB", "$depA");
    assertThrows(PatternSyntaxException.class,
        () -> Pattern.compileVariables(variables, false));
  }

  /**
   * Make sure that we can re-use variables across patterns, and that capture group
   * ordering works out correctly. This tests that the returned indices are actually
   * representative of the pattern we're running on.
   */
  @SuppressWarnings("ConstantConditions")
  @Test void reuseCaptueGroupsIndex() {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("var", "(foo) bar");
    Map<String, Pattern> compiledVars = Pattern.compileVariables(variables, false);
    Pattern before = Pattern.compile("(x) (y) $var", false, compiledVars);
    Pattern after = Pattern.compile("$var (z)", false, compiledVars);
    List<CoreLabel> input = NLPUtils.tokenize("x y foo bar z").get(0);

    Matcher<CoreLabelInputToken> afterMatcher = CoreNLPUtils.matcher(after, input);
    assertTrue(afterMatcher.find());
    assertEquals(2, afterMatcher.group(1).getBeginInclusive());
    assertEquals(4, afterMatcher.group(2).getBeginInclusive());
    assertThrows(IndexOutOfBoundsException.class, () -> afterMatcher.group(3));

    Matcher<CoreLabelInputToken> beforeMatcher = CoreNLPUtils.matcher(before, input);
    assertTrue(beforeMatcher.find());
    assertEquals(0, beforeMatcher.group(1).getBeginInclusive());
    assertEquals(1, beforeMatcher.group(2).getBeginInclusive());
    assertEquals(2, beforeMatcher.group(3).getBeginInclusive());
  }

  /**
   * Make sure that we can re-use variables across patterns, and that capture group
   * ordering works out correctly. This tests against a regression where we would
   * throw an index out of bounds exception from having a variable try to register for
   * a capture group that doesn't exist in the pattern we're matching against.
   */
  @Test void reuseCaptueGroupsBounds() {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("var", "(var)");
    Map<String, Pattern> compiledVars = Pattern.compileVariables(variables, false);
    // On the first compile, var will have index 1
    Pattern firstCompile = Pattern.compile("$var", false, compiledVars);
    // On the second compile, var will have index 3
    Pattern.compile("(x) (y) $var", false, compiledVars);
    List<CoreLabel> input = NLPUtils.tokenize("var").get(0);

    // This should work fine, but will fail if var has an index of 3
    Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(firstCompile, input);
    assertTrue(matcher.matches());
  }

  // endregion: compile variables
}
