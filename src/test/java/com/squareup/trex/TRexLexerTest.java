package com.squareup.trex;

import static com.squareup.trex.TRexParser.*;
import edu.stanford.nlp.util.StringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test the lexer for the TRex grammar.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class TRexLexerTest {

  /**
   * Lex a given input string, splitting it into appropriate tokens.
   *
   * @param input The input we're lexing.
   * @param assertType The types we need to assert for each of the tokens we expect from the output.
   *                   This can be empty, in which case we don't check any of the types.
   *
   * @return The lex'd string.
   */
  @SuppressWarnings("deprecation") private static String[] lex(String input, Integer... assertType) {
    // 1. Lex
    TRexLexer lexer = new TRexLexer(CharStreams.fromString(input));
    lexer.removeErrorListeners();
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    while (tokens.size() == 0 || tokens.get(tokens.size() - 1).getType() != Token.EOF) {
      try {
        tokens.consume();
      } catch (IllegalStateException e) {
        // case: could not parse input
        StringBuilder soFar = new StringBuilder();
        for (int i = 0; i < tokens.size(); ++i) {
          if (tokens.get(i).getType() != Token.EOF) {
            soFar.append(tokens.get(i).getText());
          }
        }
        fail("Could not lex input '" + input + "'. Parsed up through '" + soFar.toString() + "'");
      }
    }

    // 2. Collect tokens
    List<String> lst = new ArrayList<>();
    for (int i = 0; i < tokens.size(); ++i) {
      if (tokens.get(i).getType() != Token.EOF) {
        lst.add(tokens.get(i).getText());
        if (assertType.length > 0 && i < assertType.length) {
          assertTrue(tokens.get(i).getType() >= 0, "Token has no recognized type: '" + tokens.get(i).getText() + "'");
          assertEquals(assertType[i].intValue(), tokens.get(i).getType(),
              "Lexed type for token does not match on token " + i + " '" + tokens.get(i).getText() + "' (expected " + tokenNames[assertType[i]] + " but got " + tokenNames[tokens.get(i).getType()] + ")"
          );
        }
      }
    }

    // 3. Some final checks
    if (assertType.length > 0) {
      assertEquals(assertType.length, lst.size(),
          "Token count doesn't match assert count for tokens ['" + StringUtils.join(lst, "', '") + "']");
    }

    // 4. Return
    return lst.toArray(new String[0]);
  }

  /**
   * Test the quoted string literal rule
   */
  @Test void lexQuotedStringLiteral() {
    assertArrayEquals(new String[]{"\"hello"}, lex("\"hello", UnterminatedStringLiteral));
    assertArrayEquals(new String[]{"\"hello\""}, lex("\"hello\"", QuotedStringLiteral));
    assertArrayEquals(new String[]{"\"hello world\""}, lex("\"hello world\"", QuotedStringLiteral));
    assertArrayEquals(
        new String[]{"\"hello\"", "\"world\""},
        lex("\"hello\" \"world\"",
        QuotedStringLiteral, QuotedStringLiteral));
  }

  /**
   * Test escape characters in quoted strings
   */
  @Test void lexQuotedStringLiteralWithEscapes() {
    assertArrayEquals(new String[]{"\"hel\\\"lo\""},
        lex("\"hel\\\"lo\"", QuotedStringLiteral));
    assertArrayEquals(new String[]{"\"word1\\\" \\\"word2\""},
        lex("\"word1\\\" \\\"word2\"", QuotedStringLiteral));
    assertArrayEquals(new String[]{"\"hel\\\\lo\""},
        lex("\"hel\\\\lo\"", QuotedStringLiteral));
  }

  /**
   * Test special characters in quoted strings
   */
  @Test void lexQuotedStringLiteralSpecialCharacters() {
    assertArrayEquals(new String[]{"\"foo+*-?{}&|,!\""},
        lex("\"foo+*-?{}&|,!\"", QuotedStringLiteral));
  }

  /**
   * Test the regex literal rule
   */
  @Test void lexRegexLiteral() {
    assertArrayEquals(new String[]{"/hello"}, lex("/hello", UnterminatedRegexLiteral));
    assertArrayEquals(new String[]{"/hello/"}, lex("/hello/", RegexLiteral));
    assertArrayEquals(new String[]{"/\\hi/"}, lex("/\\hi/", RegexLiteral));
    assertArrayEquals(new String[]{"/\\+/"}, lex("/\\+/", RegexLiteral));
    assertArrayEquals(
        new String[]{"/hello/", "/world/"},
        lex("/hello/ /world/",
            RegexLiteral, RegexLiteral));
  }

  /**
   * Test escape characters in regex literals
   */
  @Test void lexRegexWithEscapes() {
    assertArrayEquals(new String[]{"/hel\\/lo/"},
        lex("/hel\\/lo/", RegexLiteral));
    assertArrayEquals(new String[]{"/hel\\\\lo/"},
        lex("/hel\\\\lo/", RegexLiteral));
    assertArrayEquals(new String[]{"/hello\\/ \\/world/"},
        lex("/hello\\/ \\/world/", RegexLiteral));
  }

  @Test void lexRegexWithUnicode() {

  }

  /**
   * Test special characters in regular expressions
   */
  @Test void lexRegexSpecialCharacters() {
    assertArrayEquals(new String[]{"/foo+*-?{}&|,!/"},
        lex("/foo+*-?{}&|,!/", RegexLiteral));
  }

  /**
   * Test unquoted string literal tokens
   */
  @Test void lexStringLiteral() {
    assertArrayEquals(new String[]{"hello"}, lex("hello", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"hello", "world"},
        lex("hello world", UnquotedStringLiteral, UnquotedStringLiteral));
    assertArrayEquals(new String[]{"."}, lex(".", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"foo42"}, lex("foo42", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"hello-world"}, lex("hello-world", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"~@"}, lex("~@", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"-42"}, lex("-42", Number));
    assertArrayEquals(new String[]{"-LRB-"}, lex("-LRB-", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"a-3"}, lex("a-3", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"-"}, lex("-", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"--"}, lex("--", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"--foo--"}, lex("--foo--", UnquotedStringLiteral));
    assertArrayEquals(new String[]{","}, lex(",", Comma));
    assertArrayEquals(new String[]{",", ","}, lex(",,", Comma, Comma));
  }

  /**
   * Test parsing numbers
   */
  @Test void lexNumber() {
    assertArrayEquals(new String[]{"42"}, lex("42", Number));
    assertArrayEquals(new String[]{"0"}, lex("0", Number));
    assertArrayEquals(new String[]{"-3"}, lex("-3", Number));
    assertArrayEquals(new String[]{"--3"}, lex("--3", Number));
  }

  /**
   * Test parsing numeric operators (e.g., <=)
   */
  @Test void lexNumericOperators() {
    assertArrayEquals(new String[]{"<"}, lex("<", LT));
    assertArrayEquals(new String[]{"<", "="}, lex("<=", LT, EQ));
    assertArrayEquals(new String[]{">"}, lex(">", GT));
    assertArrayEquals(new String[]{">", "="}, lex(">=", GT, EQ));
    assertArrayEquals(new String[]{"="}, lex("=", EQ));
    assertArrayEquals(new String[]{"=", "="}, lex("==", EQ, EQ));
    assertArrayEquals(new String[]{">", "=", "="}, lex(">==", GT, EQ, EQ));
    assertArrayEquals(new String[]{"!", "="}, lex("!=", Not, EQ));
  }

  /**
   * Test the plus, star, and curly brace repeat operators
   */
  @Test void lexRepeatMarkers() {
    assertArrayEquals(new String[]{"foo", "+"},
        lex("foo+", UnquotedStringLiteral, Plus));
    assertArrayEquals(new String[]{"foo", "+", "?"},
        lex("foo+?", UnquotedStringLiteral, Plus, QMark));
    assertArrayEquals(new String[]{"foo", "*"},
        lex("foo*", UnquotedStringLiteral, Star));
    assertArrayEquals(new String[]{"foo", "*", "?"},
        lex("foo*?", UnquotedStringLiteral, Star, QMark));
    assertArrayEquals(new String[]{"foo", "?"},
        lex("foo?", UnquotedStringLiteral, QMark));
    assertArrayEquals(new String[]{"foo", "?", "?"},
        lex("foo??", UnquotedStringLiteral, QMark, QMark));
    assertArrayEquals(new String[]{"foo", "*", "*"},
        lex("foo**", UnquotedStringLiteral, Star, Star));
    assertArrayEquals(new String[]{"\"foo*\"", "*"},
        lex("\"foo*\"*", QuotedStringLiteral, Star));
    assertArrayEquals(new String[]{"{", "0", ",", "10", "}"},
        lex("{0,10}", OpenBrace, Number, Comma, Number, CloseBrace));
  }


  /**
   * Test parsing variables
   */
  @Test void lexVariable() {
    assertArrayEquals(new String[]{"$foo"}, lex("$foo", Variable));
    assertArrayEquals(new String[]{"$foo_bar"}, lex("$foo_bar", Variable));
    assertArrayEquals(new String[]{"$foo-bar"}, lex("$foo-bar", Variable));
    assertArrayEquals(new String[]{"$foo42"}, lex("$foo42", Variable));
    assertArrayEquals(new String[]{"$", "42"}, lex("$42", Dollar, Number));
    assertArrayEquals(new String[]{"$-", "42"}, lex("$-42", Variable, Number));
  }

  /**
   * Test that we can parse a begin token (^) and an end token ($) appropriately.
   */
  @Test void lexStartEndToken() {
    assertArrayEquals(new String[]{"^"}, lex("^", Caret));
    assertArrayEquals(new String[]{"$"}, lex("$", Dollar));
    assertArrayEquals(new String[]{"^", "$"}, lex("^$", Caret, Dollar));
    assertArrayEquals(new String[]{"$", "foo"}, lex("$ foo", Dollar, UnquotedStringLiteral));
    assertArrayEquals(new String[]{"^", "foo"}, lex("^ foo", Caret, UnquotedStringLiteral));
    assertArrayEquals(new String[]{"^", "foo", "$"},
        lex("^ foo $", Caret, UnquotedStringLiteral, Dollar));
    assertArrayEquals(new String[]{"^", "foo", "$"},
        lex("^foo$", Caret, UnquotedStringLiteral, Dollar));
    assertArrayEquals(new String[]{"$", "|", "x"},
        lex("$|x", Dollar, Or, UnquotedStringLiteral));
  }

  /**
   * Lex some potentially ambiguous or difficult examples
   */
  @Test void lexComments() {
    assertArrayEquals(new String[]{"foo"}, lex("foo // comment", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"foo"}, lex("foo //", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"foo"}, lex("foo // ", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"foo"}, lex("foo /* comment */", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"foo"}, lex("foo /* */", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"foo"}, lex("foo /**/", UnquotedStringLiteral));
    assertArrayEquals(new String[]{"foo", "bar"},
        lex("foo /* comment */ bar", UnquotedStringLiteral, UnquotedStringLiteral));
  }

  /**
   * A helper for producing a dynamic test case.
   */
  private static DynamicTest check(String[] expected, String toLex, Integer... labels) {
    return DynamicTest.dynamicTest(toLex, () -> assertArrayEquals(
        expected,
        lex(toLex, labels)
    ));
  }

  /**
   * Lex some potentially ambiguous or difficult examples
   */
  @TestFactory Iterable<DynamicTest> lexExamples() {
    return Arrays.asList(
        check(new String[]{"[", "!", "{", "foo", ":", "8", "}", "]", "{", "0", ",", "5", "}"},
            "[!{foo:8}]{0,5}",
            OpenSquare, Not, OpenBrace, UnquotedStringLiteral, Colon,
            Number, CloseBrace, CloseSquare,
            OpenBrace, Number, Comma, Number, CloseBrace),
        check(new String[]{"foo8", "[", "]"},
            "foo8[]",
            UnquotedStringLiteral, OpenSquare, CloseSquare),
        check(new String[]{"(", "?", "<", "hello", ">", "foo", ")"},
            "(?<hello>foo)",
            OpenParen, QMark, LT, UnquotedStringLiteral, GT, UnquotedStringLiteral, CloseParen),
        check(new String[]{"(","?", "$hello", "foo", ")"},
            "(?$hello foo)",
            OpenParen, QMark, Variable, UnquotedStringLiteral, CloseParen)
    );
  }

}
