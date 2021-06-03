package com.squareup.trex;

import java.util.Arrays;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the various parser rules in the TRex parser.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class TRexParserTest {

  /**
   * Run the parser on a given input.
   * This runs all the appropriate checks to make sure the parse was successful.
   *
   * @param input The input to run the parser on.
   * @param fn The rule to run -- this is a function from a parser to a particular terminal to get.
   * @param <E> The type of terminal we're getting.
   *
   * @return The terminal, if the parse was successful.
   */
  @SuppressWarnings("UnusedReturnValue")
  private static <E extends ParserRuleContext> DynamicTest parse(
      String input,
      Function<TRexParser, E> fn) {
    return DynamicTest.dynamicTest("should parse: " + input, () -> {
      TRexLexer lexer = new TRexLexer(CharStreams.fromString(input));
      lexer.removeErrorListeners();
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      TRexParser parser = new TRexParser(tokens);
      parser.removeErrorListeners();
      E result = fn.apply(parser);
      assertNull(result.exception,
          "Could not parse input '" + input + "': " + (result.exception == null
              ? ""
              : result.exception)
      );
      assertEquals(0, parser.getNumberOfSyntaxErrors(),
          "Encountered syntax errors parsing '" + input + "'");
      // note: this does not test that we maintain whitespace in regex+quotes
      assertEquals(
          input.replaceAll("\\s+", ""),
          result.getText().replaceAll("\\s+", ""),
          "We did not consume the whole input");
    });
  }


  /**
   * Ensure that the given input doesn't parse for the given rule.
   *
   * @param input The input to try to parse.
   * @param fn The root rule to try to use for the parsing.
   * @param <E> The type of result we'd be expecting
   */
  private static <E extends ParserRuleContext> DynamicTest noParse(
      String input,
      Function<TRexParser, E> fn) {
    return DynamicTest.dynamicTest("should not parse: " + input, () -> {
      TRexLexer lexer = new TRexLexer(CharStreams.fromString(input));
      lexer.removeErrorListeners();
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      TRexParser parser = new TRexParser(tokens);
      parser.removeErrorListeners();
      E result = fn.apply(parser);
      if (input.equals(result.getText())) {  // if we parsed the whole input
        assertTrue(result.exception != null || parser.getNumberOfSyntaxErrors() > 0,
            "Input parsed but should not have: " + input);
      }
    });
  }

  /**
   * Check that the given input pattern doesn't compile.
   *
   * @param input The input to try to parse.
   */
  private static DynamicTest noCompile(String input) {
    return DynamicTest.dynamicTest("should not compile: " + input, () -> {
      TRexLexer lexer = new TRexLexer(CharStreams.fromString(input));
      lexer.removeErrorListeners();
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      TRexParser parser = new TRexParser(tokens);
      parser.removeErrorListeners();
      assertEquals(0, parser.getNumberOfSyntaxErrors(), "Input should not have parsed");
    });
  }

  /**
   * Test parsing a single value.
   */
  @TestFactory Iterable<DynamicTest> value() {
    return Arrays.asList(
        parse("hello", TRexParser::value),
        parse("~#%-", TRexParser::value),
        parse("\"hello world\"", TRexParser::value),
        parse("\"hello\\\" \\\"world\"", TRexParser::value),
        parse("/hello world/", TRexParser::value),
        parse("/hello\\/world/", TRexParser::value),
        parse("42", TRexParser::value),
        parse("foo42", TRexParser::value),
        parse("/\\+/", TRexParser::value),  // regex special character
        noParse("hello world", TRexParser::value)
    );
  }

  /**
   * Test parsing a key value pair
   */
  @TestFactory Iterable<DynamicTest> keyValuePair() {
    return Arrays.asList(
        parse("{foo: bar}", TRexParser::key_value_pair),
        parse("{foo: \"bar\"}", TRexParser::key_value_pair),
        parse("{foo: /bar/}", TRexParser::key_value_pair),
        noParse("{\"foo\": bar}", TRexParser::key_value_pair),
        noParse("{/foo/: bar}", TRexParser::key_value_pair),
        noParse("{foo bar: baz}", TRexParser::key_value_pair),
        noParse("foo: bar", TRexParser::key_value_pair)
    );
  }

  /**
   * Test parsing a token body. This implicitly tests
   * token body atom.
   */
  @TestFactory Iterable<DynamicTest> tokenBody() {
    return Arrays.asList(
        parse("{foo: bar}", TRexParser::token_body),
        parse("({foo: bar})", TRexParser::token_body),
        parse("(!{foo: bar})", TRexParser::token_body),
        parse("{foo: bar} & {bar: baz}", TRexParser::token_body),
        parse("{foo: bar} | {bar: baz}", TRexParser::token_body),
        parse("{foo: bar} & {bar: baz} | {a: b}", TRexParser::token_body),
        parse("({foo: bar} & {bar: baz}) | {a: b}", TRexParser::token_body),
        parse("{foo: bar} & ({bar: baz} | {a: b})", TRexParser::token_body),
        parse("{a:b} & ({b:c} | {d:e}) | {e:f}", TRexParser::token_body),
        parse("({a:b} & ({b:c} | ({d:e}))) | {e:f}", TRexParser::token_body),
        parse("({a:b} & !({b:c} | ({d:e}))) | {e:f}", TRexParser::token_body),
        parse("foo: bar | foo: baz", TRexParser::token_body),
        parse("foo: bar | foo: /baz/", TRexParser::token_body),
        parse("foo: bar | foo: \"baz\"", TRexParser::token_body),
        parse("foo: bar", TRexParser::token_body),
        parse("foo: bar & key: \"value\"", TRexParser::token_body),
        parse("{!foo}", TRexParser::token_body),
        parse("!foo", TRexParser::token_body),
        parse("key >= 7", TRexParser::token_body),
        parse("key=2", TRexParser::token_body),
        parse("key<=2", TRexParser::token_body),
        parse("!foo & (key >= 8 | a: b)", TRexParser::token_body),
        noParse("({a:b}", TRexParser::token_body),
        noParse("({a:b}", TRexParser::token_body),
        noParse("({a:b}))", TRexParser::token_body),
        noParse("!{foo}", TRexParser::token_body),
        noParse("!foo: bar", TRexParser::token_body),
        noParse("{foo: bar} | baz", TRexParser::token_body),
        noParse("{foo: bar} | /baz/", TRexParser::token_body),
        noParse("{foo: bar} | \"baz\"", TRexParser::token_body)
    );
  }

  /**
   * Test parsing a token
   */
  @TestFactory Iterable<DynamicTest> token() {
    return Arrays.asList(
        parse("[{a:b}]", TRexParser::token),
        parse("[{a:b} | {c:d}]", TRexParser::token),
        parse("[]", TRexParser::token),
        parse("foo", TRexParser::token),
        parse("/foo/", TRexParser::token),
        parse("\"foo bar\"", TRexParser::token),
        parse("42", TRexParser::token),
        parse("^", TRexParser::token),
        parse("$", TRexParser::token),
        parse(":", TRexParser::token),
        parse("::", TRexParser::token),
        parse("<", TRexParser::token),
        parse("<=", TRexParser::token),
        parse("=", TRexParser::token),
        parse("==", TRexParser::token),
        parse("=:", TRexParser::token),

        noParse("{", TRexParser::token),
        noParse("}", TRexParser::token),
        noParse("[", TRexParser::token),
        noParse("{foo:bar}", TRexParser::token),
        noParse("[]]", TRexParser::token),
        noParse("\"foo", TRexParser::token),
        noParse("\"\"", TRexParser::token),

        noCompile("[{word::]"),
        noCompile("[{word:{]"),
        noCompile("[{word:}]")
    );
  }

  /**
   * Test parsing a repeated atom
   */
  @TestFactory Iterable<DynamicTest> repeatAtom() {
    return Arrays.asList(
        parse("[]{0,10}", TRexParser::atom),
        parse("[]{0,10}?", TRexParser::atom),
        // Note: this is a valid atom, But semantically distinct from the above
        parse("[]{0,10}?", TRexParser::atom),
        parse("[]{10}", TRexParser::atom),
        parse("[]{10,}", TRexParser::atom),
        parse("[]{10,}?", TRexParser::atom),
        parse("foo{0,10}", TRexParser::atom),
        parse("foo{0,10}?", TRexParser::atom),
        parse("\"foo\"{0,10}", TRexParser::atom),
        parse("\"foo\"{0,10}?", TRexParser::atom),
        parse("/foo/{0,10}", TRexParser::atom),
        parse("/foo/{0,10}?", TRexParser::atom),
        parse("[{a:b}]{10}", TRexParser::atom),
        parse("[{a:b} & ({b:c} | {d:e})]{10}", TRexParser::atom),
        // note[gabor]: negative repeat values must be validated application-side
        parse("[]{-2,5}", TRexParser::atom),
        // note[gabor]: invalid ranges must be validated application side
        parse("[]{5,2}", TRexParser::atom),
        parse("[]{-2,-5}", TRexParser::atom),
        // These should not parse
        noParse("{}", TRexParser::atom),
        noParse("{,}", TRexParser::atom),
        noParse("{0,10}", TRexParser::atom)
    );
  }

  /**
   * Test parsing a plus (one or more repeat) atom
   */
  @TestFactory Iterable<DynamicTest> plusAtom() {
    return Arrays.asList(
        parse("[]+", TRexParser::atom),
        parse("foo+", TRexParser::atom),
        parse("\"foo\"+", TRexParser::atom),
        parse("/foo/+", TRexParser::atom),
        parse("foo+?", TRexParser::atom),
        parse("[{a:b}]+", TRexParser::atom),
        parse("[{a:b} & ({b:c} | {d:e})]+", TRexParser::atom),
        noParse("+", TRexParser::atom),
        noParse("++", TRexParser::atom)
    );
  }

  /**
   * Test parsing a star (zero or more repeat) atom
   */
  @TestFactory Iterable<DynamicTest> starAtom() {
    return Arrays.asList(
        parse("[]*", TRexParser::atom),
        parse("foo*", TRexParser::atom),
        parse("\"foo\"*", TRexParser::atom),
        parse("/foo/*", TRexParser::atom),
        parse("foo*?", TRexParser::atom),
        parse("[{a:b}]*", TRexParser::atom),
        parse("[{a:b} & ({b:c} | {d:e})]*", TRexParser::atom),
        noParse("*", TRexParser::atom),
        noParse("**", TRexParser::atom)
    );
  }

  /**
   * Test parsing an atom with a question mark (e.g., []?)
   */
  @TestFactory Iterable<DynamicTest> qmarkAtom() {
    return Arrays.asList(
        parse("[]?", TRexParser::atom),
        parse("foo?", TRexParser::atom),
        parse("\"foo\"?", TRexParser::atom),
        parse("/foo/?", TRexParser::atom),
        parse("[{a:b}]?", TRexParser::atom),
        parse("[{a:b} & ({b:c} | {d:e})]?", TRexParser::atom),
        noParse("?", TRexParser::atom),
        noParse("??", TRexParser::atom)
    );
  }

  /**
   * Test parsing a parenthetical element
   */
  @TestFactory Iterable<DynamicTest> parenthetical() {
    return Arrays.asList(
        parse("([])", TRexParser::parenthetical),
        parse("([{a: b}] [{c:d}])", TRexParser::parenthetical),
        parse("(a b c)", TRexParser::parenthetical),
        parse("(a /b/ \"c\")", TRexParser::parenthetical),
        parse("(a (b c) d)", TRexParser::parenthetical),
        parse("(a{0,9} (b c)* d+)", TRexParser::parenthetical),
        parse("(?<group> hello)", TRexParser::parenthetical),
        parse("(?<group_name> hello)", TRexParser::parenthetical),
        parse("(?$group hello)", TRexParser::parenthetical),
        parse("(?$group_name hello)", TRexParser::parenthetical),
        // group names cannot contain spaces
        noParse("(?<group name> hello)", TRexParser::parenthetical)
    );
  }

  /**
   * Test parsing an atom
   */
  @TestFactory Iterable<DynamicTest> atom() {
    return Arrays.asList(
        parse("[]", TRexParser::atom),
        parse("foo", TRexParser::atom),
        parse("\"foo\"", TRexParser::atom),
        parse("/foo/", TRexParser::atom),
        parse("([{a: b}] [{c:d}])", TRexParser::atom),
        parse("(foo bar)", TRexParser::atom),
        parse("(foo (bar /baz/))", TRexParser::atom),
        parse("$FOO", TRexParser::atom),
        parse("$foo_bar", TRexParser::atom),
        parse("foo+", TRexParser::atom),
        parse("foo*", TRexParser::atom),
        parse("foo{0,10}", TRexParser::atom),
        parse("([{a: b}] [{c:d}])", TRexParser::atom),
        parse("(foo bar)", TRexParser::atom),
        parse("(foo (bar /baz/))", TRexParser::atom),
        parse("(foo (bar /baz/)+)", TRexParser::atom),
        parse("(foo (bar /baz/)+)*", TRexParser::atom),
        parse("(foo (bar /baz/)+)+", TRexParser::atom),
        parse("(foo (bar /baz/)+){0,10}", TRexParser::atom),

        noParse("[foo]", TRexParser::atom),
        noParse("[/foo/]", TRexParser::atom),
        noParse("[\"foo\"]", TRexParser::atom)
    );
  }

  /**
   * Test parsing a list of atoms, forming a complete pattern.
   */
  @TestFactory Iterable<DynamicTest> atomList() {
    return Arrays.asList(
        parse("[]", TRexParser::pattern),
        parse("[] []", TRexParser::pattern),
        parse("foo bar", TRexParser::pattern),
        parse("foo \"bar\" /baz/", TRexParser::pattern),
        parse("[{word:foo}] \"bar\" /baz/", TRexParser::pattern),
        parse("([({word:foo} | {lemma:food})] \"bar\")+ /baz/{0,3}",
            TRexParser::pattern),
        parse("[] $FOO bar", TRexParser::pattern)
    );
  }

  /**
   * Test parsing a conjunction
   */
  @TestFactory Iterable<DynamicTest> conjunction() {
    return Arrays.asList(
        parse("[] & []", TRexParser::pattern),
        parse("foo & bar", TRexParser::pattern),
        parse("(foo & bar) & baz", TRexParser::pattern),
        parse("foo & (bar & baz)", TRexParser::pattern),
        parse("foo bar & a b", TRexParser::pattern),
        parse("foo bar & a b & x y z", TRexParser::pattern),
        parse("(foo bar & a) x & q r s", TRexParser::pattern),

        noParse("foo &", TRexParser::pattern),
        noParse("foo & bar &", TRexParser::pattern)
    );
  }

  /**
   * Test parsing a disjunction
   */
  @TestFactory Iterable<DynamicTest> disjunction() {
    return Arrays.asList(
        parse("[] | []", TRexParser::pattern),
        parse("foo | bar", TRexParser::pattern),
        parse("(foo | bar) | baz", TRexParser::pattern),
        parse("foo | (bar | baz)", TRexParser::pattern),
        parse("foo bar | a b", TRexParser::pattern),
        parse("foo bar | a b | x y z", TRexParser::pattern),
        parse("(foo bar | a) x | q r s", TRexParser::pattern),

        noParse("foo |", TRexParser::pattern),
        noParse("foo | bar |", TRexParser::pattern)
    );
  }

}
