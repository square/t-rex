/**
 * The T-Rex grammar.
 * This will parse a regular expression over a list of tokens.
 * A token is defined to be a mapping from string to string values. Commonly,
 * this is a single token in a sentence, with all of the NLP annotations on that token
 * encoded into a string to string map.
 *
 * Many of the common regular expression contructs (conjunction, disjunction,
 * dot-star matching, etc.) are implemented here over token lists. Furthermore,
 * note that each token itself can be a complex expression, including conjunction
 * and disjunctions of conditions, or even a regular expression over the value of
 * one of the token keys.
 *
 * Some examples of things recognized:
 *
 * Patterns (the root type):
 *  - hello world
 *    ^ matches the two tokens [hello, world]
 *  - /hello|goodbye/ world
 *    ^ matches either hello world, or goodbye world
 *  - (hello | goodbye) world
 *    ^ matches either hello world, or goodbye world. Another way to write
 *      the pattern above, but with the disjunction on the token level.
 *  - the server is (ok | on fire)
 *    ^ matches either "the server is ok", or "the server is on fire".
 *  - the server is (phone number & [{pos:NN}]+)
 *    ^ matches "phone number" but only if both "phone" and "number" are nouns.
 *  - [{pos:/NN.?.?/} | {pos:JJ}]+
 *    ^ matches a string of tokens that are either nouns or adjectives.
 *
 * Single token:
 *  - "hello"
 *  - hello
 *  - /hello/
 *  - [{word:"hello"}]
 *  - [{word:hello}]
 *  - [{word:/hello/}]
 *  - [{word:/hello/} & {pos:NN}]
 *
 * Literal strings:
 *  - "hello"
 *  - hello
 *  - NOT: 'hello'
 *
 * Regular expressions:
 *  - /hello/
 *  - /(?iu)hello/
 *
 * Common Gotchas:
 *   - Regular expressions are always case sensitive, even if a
 *     case insensitive match is requested. If a case-insensitive
 *     match is desired, it should be encoded into the regex expression.
 *     with /(?i).../.
 *
 * <b>IMPORTANT NOTE:</b> if you add rules / branches of rules to this file,
 * be sure to also update the associated Visitor classes in the implementing
 * languages (for Java: in Pattern.java).
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */

grammar TRex;

options {
  language = Java;
}

@header {
package com.squareup.trex;
}

//
//  ----- PARSER -----
//

/**
 * The top-level rule for parsing a complete pattern.
 * A pattern is either a list of atoms (tokens or atomic groups
 * of tokens), or else a boolean expression of patterns. To
 * ensure deterministic parsing, boolean expressions are left
 * branching.
 */
pattern
  : atom+              # atom_list
  | atom+ And pattern  # conjunction
  | atom+ Or pattern   # disjunction
  ;

/**
 * A single atom in a pattern. This is canonically a single token,
 * but it can also be a paranthetical expression, a repeat expression,
 * or a variable.
 *
 * Examples of atoms:
 *   - [{pos:foo}]
 *   - [{pos:foo}]+
 *   - (hello there)
 *   - (hello there)+
 *   - (hello there)+?
 *   - $FOO
 */
atom
  : token                # token_atom
  | parenthetical        # paren_atom
  // note[gabor] atom{n,m}? has precedence over atom?
  | atom OpenBrace Number Comma Number CloseBrace QMark?  # repeat_atom_bounded
  // note[gabor] atom{n,}? has precedence over atom?
  | atom OpenBrace Number Comma CloseBrace QMark?         # repeat_atom_unbounded
  // note[gabor] atom{n}? has precedence over atom?
  | atom OpenBrace Number CloseBrace QMark?               # repeat_atom_exact
  | atom Star QMark?     # star_atom
  | atom Plus QMark?     # plus_atom
  | atom QMark QMark?    # qmark_atom
  | Variable             # variable_atom
  ;

/**
 * A paranthetical expression. This groups a set of atoms
 * together into a single atom.
 * This also defines a capture group, either anonymous or named.
 */
parenthetical
  : OpenParen pattern CloseParen                                     # anonymous_parenthetical
  // This is the canonical syntax for named capture groups.
  | OpenParen QMark LT UnquotedStringLiteral GT pattern CloseParen   # named_parenthetical
  // This syntax diverges from the equivalent syntax in regular expressions, but is
  // used in Angel's original TokensRegex. For backwards compatibility, we parse it here
  // as well.
  | OpenParen QMark Variable pattern CloseParen  # legacy_named_parenthetical
  ;

/**
 * A single token. This can be represented in a number
 * of ways, but is in general an expression to match
 * one token (or the special case of ^ and $ matching the
 * beginning and end of the sequence, respectively).
 *
 * Examples of tokens:
 *   - cat
 *   - "cat"
 *   - [{word:"cat"}]
 *   - []
 *   - ^
 *   - $
 */
token
  : value                              # literal_token
  | OpenSquare token_body CloseSquare  # complex_token
  | OpenSquare CloseSquare             # wildcard_token
  | Caret                              # start_token
  | Dollar                             # end_token
  // The values below are safe to parse as a value if they're not in the context of
  // a token.
  | (Colon | numeric_op | Not)+        # keyword_token
  ;

/**
 * The body of a token. This is the portion inside of the
 * square brackets. For example, {word:cat}&{pos:NN}.
 * This can be a boolean expression of conditions,
 * in which case the expression is left-branching.
 */
token_body
  : token_body_atom                 # token_body_simple
  | token_body And token_body_atom  # token_body_conjunction
  | token_body Or token_body_atom   # token_body_disjunction
  ;

/**
 * An atomic element of a token body, that can be combined with
 * and (&) or or (|) to form a token body. This is broken out to
 * enforce that the grammar is deterministically branching.
 */
token_body_atom
  : key_value_pair                   # token_body_atom_simple
  | OpenParen token_body CloseParen  # token_body_atom_paren
  | Not token_body_atom              # token_body_atom_negated
  ;

/**
 * An expression to match a single key to a value. This includes numeric
 * comparators, or checking if the value is null / not present in the map
 * For example, {word:cat}, or {index>5}, or {!lemma}.
 *
 * Note that a key value pair doesn't need to be surrounded by braces; the braces
 * are provided for syntactic clarity and backwards-compatibility with TokensRegex.
 */
key_value_pair
  : OpenBrace braceless_key_value_pair CloseBrace
  | braceless_key_value_pair
  ;

/**
 * The implementation of a key_value_pair above, but without the requirement to
 * have braces around the expression.
 */
braceless_key_value_pair
  : UnquotedStringLiteral Colon value        # key_value_string
  | UnquotedStringLiteral numeric_op Number  # key_value_numeric
  | Not UnquotedStringLiteral                # key_value_null
  ;

/**
 * A value that a property of a token can match against.
 */
value
  : UnquotedStringLiteral # value_unquoted
  | QuotedStringLiteral   # value_quoted
  | RegexLiteral          # value_regex
  | Number                # value_number
  // A comma is tokenized separately, but it's a valid value if it's not in
  // the context of a repeat expression.
  | Comma                 # value_comma
  ;

/**
 * A numeric operator between two numbers. For example, '<=' or '>'. For flexibility,
 * this accepts both '=' and '==' as valid equality operators.
 */
numeric_op : (GT | LT | EQ) EQ? | Not EQ;

//
//  ----- LEXER -----
//

/**
 * A variable. This is used to refer to another parsed fragment
 * in our environment to make patterns more readable. This looks
 * like the following, or similar:
 *
 *     $foo
 *     $foo_bar
 */
Variable
  : Dollar UnquotedStringLiteral
  ;

/**
 * A regex literal. For example, /f.+/ or /(b{2})+/.
 */
RegexLiteral
  : UnterminatedRegexLiteral '/'
  ;

/**
 * A component of the regex literal above. This is broken
 * out to allow for escaped '/' characters in the regex.
 */
UnterminatedRegexLiteral
  : '/'  // regexes start with a slash
    ~[/\r\n*]  // (1) we cannot have an empty regex, and (2) /* is the start of
               // a comment and therefore not a valid regex
    (~[/\r\n] | '\\' (. | EOF))*  // consume the rest of the regex
  ;

/**
 * A quoted string literal. For example, "foo" or "foo bar".
 * Note that double quotes (") are the only valid quote character.
 */
QuotedStringLiteral
  : UnterminatedStringLiteral '"'
  ;

/**
 * A component of the quoted string literal above. This is broken
 * out to allow for escaped quote characters in the string.
 */
UnterminatedStringLiteral
  : '"' (~["\\\r\n] | '\\' (. | EOF))+  // note: empty quotes are not allowed
  ;

/**
 * A numeric value. This can either be a bare number in a pattern, for
 * example, in the following pattern to match phone numbers with the
 * 626 area code -- the first token is a Number token:
 *
 *     626 - /[0-9]{3}/ - /[0-9]{4}/
 *
 * Or, this number is lexed in the repeat specification:
 *
 *     []{0,10}
 */
Number
  : '-'* [0-9]+
  ;

/**
 * An unquoted string literal. This is a bare expression that we can
 * safely parse as a string, as distinct from a number, regex, or special
 * character of some sort. For example, the following pattern is entirely
 * unquoted string literals:
 *
 *     foo bar baz
 */
UnquotedStringLiteral
  // A string that starts with an unambiguously string character, followed
  // by some characters which could otherwise be confused to be numbers, etc.
  // This should match things like "foo", "a42", "a-b", "a-42", etc.
  : (~[0-9\-[\]{}():&|!+*?,$^"<>=/ \t\r\n\u000C]) (~[[\]{}():&|!+*?,$^"<>= \t\r\n\u000C])*
  // In some special cases, we allow a string to start with '-'. The following
  // character can't be a number (or other special character)
  | '-'+ (~[0-9\-[\]{}():&|!+*?,$^"<>=/ \t\r\n\u000C]) (~[[\]{}():&|!+*?,$^"<>= \t\r\n\u000C])*
  // A pure dash without anything after it is technically a valid string literal
  // as well.
  | '-'+
  ;

OpenBrace   : '{';
CloseBrace  : '}';
OpenSquare  : '[';
CloseSquare : ']';
OpenParen   : '(';
CloseParen  : ')';
Colon       : ':';
And         : '&';
Or          : '|';
Not         : '!';
Plus        : '+';
Star        : '*';
QMark       : '?';
Comma       : ',';
Dollar      : '$';
Caret       : '^';
LT          : '<';
GT          : '>';
EQ          : '=';

/**
 * We toss out all comments
 */
Comment : '/' '/' (EOF | (~[\r\n])*) -> skip;
BlockComment
  : UnterminatedBlockComment Star '/' -> skip
  ;
UnterminatedBlockComment
  : '/' Star (~[*\\\r\n] | '\\' (. | EOF))*
  ;

/**
 * We toss out all whitespace
 */
Whitespace : [ \t\r\n\u000C]+ -> skip;

