# T-Rex: A regex language over rich tokens

T-Rex is a library that allows you to write regular expressions over tokens
which themselves have substructure. The motivating use-case is for natural
language tokens: a word may have a lemma, part of speech, named entity tag,
etc attached to it. T-Rex allows you to match against these properties using
an enriched regular expression language. For example, the following pattern
can be used to extract "born in" relations that are written in a variety
of ways:

```
[{ner:PERSON}]+ [{lemma:be}] born (on [!{pos:/VB.?/}])? in [{ner:LOCATION}]+
```

This would match:

  * Obama was born in Hawaii
  * Obama was born on August 4, 1961, in Hawaii
  * Barack Obama was born in Honolulu, Hawaii
  * ...
    
At a high level, T-Rex is a regular expression language over a sequence of
tokens. Each token is itself a mapping from string keys to string values.
Therefore, matching a token can itself be a complex expression of the various
values in that token. Patterns can be complex, like the pattern above, or as 
simple as a string of words matching an exact sentence. For example, the 
following is a perfectly valid T-Rex pattern:

```
Obama was born in Hawaii
```


This package is almost entirely API-compatible with
[TokensRegex](https://nlp.stanford.edu/software/tokensregex.shtml) by [Angel
Chang](https://angelxuanchang.github.io/), released as part of CoreNLP. However,
T-Rex is roughly 40x faster than TokenxRegex, supports timing out
long-running queries, and defines a re-usable Antlr grammar that can be used to
port the system in other programming languages.

The following sections will describe the T-Rex grammar, code usage, and
the high-level structure of the implementation.

## Quick-Start

### Installation

T-Rex has only a single dependency: the
[Antlr 4 runtime](https://mvnrepository.com/artifact/org.antlr/antlr4).
If you include the Antlr library and the `trex.jar` in your classpath, you should
have everything you need to get started. You can also add T-Rex via Maven or Gradle.

#### Maven
```xml
<dependency>
    <groupId>com.squareup.trex</groupId>
    <artifactId>trex</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### Gradle
```gradle
implementation group: 'com.squareup.trex', name: 'trex', version: '4.2.2'
```

(note: for older versions of Gradle, use `compile` instead of `implementation` above).

### Usage
T-Rex aims to mirror the API of the standard SDK regular expression library as much
as possible. The three main components are `Pattern` (the regular expression),
`Matcher` (the state of a regex match against a sequence), and `String` (the sequence
to match against). The equivalents of each of these in T-Rex is:

| Java SDK                     | T-Rex                                              |
| ---------------------------- | -------------------------------------------------- |
| java.util.regex.Pattern      | com.squareup.trex.Pattern                          |
| java.util.regex.Matcher      | com.squareup.trex.Matcher                          |
| String                       | List<? extends com.squareup.trex.TRexInputToken>  |

A T-Rex matcher can be created as below. We use a simple implementation of 
`TRexInputToken` backed by a Java `Map` object: `MapTRexInputToken`, and we use a
pattern that searches for the word "hello" followed by either the word "world" or
any number of tokens which have value "Star Wars" at key "movie":

```java
Pattern pattern = com.squareup.trex.Pattern.compile("hello (world | [{movie: \"Star Wars\"}]+)");

List<MapTRexInputToken> input = List.of(
    new MapTRexInputToken(Map.of("default", "hello", "movie", "Star Wars")),
    new MapTRexInputToken(Map.of("default", "there", "movie", "Star Wars"))
);

Matcher<MapTRexInputToken> matcher = pattern.matcher(input);
if (matcher.matches()) {
  Matcher.CaptureGroup<MapTRexInputToken> match = matcher.group(0);
  // Prints "[0, 2)"
  System.out.println(match.toString());
  // Prints "[{default=hello, movie=Star Wars}, {default=there, movie=Star Wars}]"
  System.out.println(match.matchedTokens().toString());
}
```

### CoreNLP Quickstart
If you have CoreNLP in your classpath, a `TRexInputToken` type is included in
T-Rex to make matching against lists of `CoreLabel` objects easier. You can create
a matcher using the code below:

```java
Pattern pattern = com.squareup.trex.Pattern.compile("hello [{ner: PERSON}]");
List<CoreLabel> input = ...;
Matcher<CoreLabelInputToken> matcher = CoreNLPUtils.matcher(pattern, input);
```

## Grammar
T-Rex is a regular expression language over tokens. This section begins by
describing the structure of a token, and then the operators that are supported
over lists of tokens.

### Token
A token is the atomic unit for the regular expression language. You can think of
this as a single word in a natural language sentence. A token is, at its core, a
mapping of string keys to string values. The anatomy of a token reflects this.
Every token is delimited with square brackets `[]`, and within these brackets,
key+value pairs are delimited inside curly braces `{}`. The simplest token
therefore looks like:

```
[{key:value}]
```

As syntactic sugar for readability, a default key (by default, named `default`)
is used to key any value that doesn't have an explicit key associated with it.
So, the regex `value` is really an alias for `[{default:value}]`.

Keys of a token must be unquoted and cannot contain any of the T-Rex
special characters
(`-`,`[`,`]`,`{`,`}`,`(`,`)`,`:`,`&`,`|`,`!`,`+`,`*`,`?`,`,`,`$`,`^`,`"`, or
whitespace). Values can either be unquoted, quoted (e.g., `[{key:"value"}]`), or
themselves be regular expressions (e.g.,`[{key:/[Vv]alue/}]`). Quoted values
must be quoted with double quotes.

In addition to matching a single key+value pair, a token can express a simple
propositional algebra of conditions that must be met for the token. This
includes conjunction, disjunction, and negation of key-value pairs, as well as
grouping conditions with parentheses. For example, the following will match a
token whose `word` is either `play` or `run`, but whose `pos` (part of speech
tag) is not `NN`. That is, it'll match the verb form of "play" or "run": 

```
[({word:play} | {word:run}) & !{pos:NN}]
```

Tokens can also compare numeric values. For example, the following might check
if a token's key is greater than 10: `[{key>10}]`. Only integer comparisons are
supported, in order to avoid introducing `.` as special characters in the
grammar.

There's also a wildcard pattern which will match any token, which is defined as
the token pattern with no values to match: `[]`.

Lastly, a token can be a variable. Variables are prefixed with '$', and then
followed by an unquoted string -- with special characters disallowed same as key
specifications. For example, `$FOO` or `$FOO_BAR` are valid variables.

The full list of types of tokens are therefore:

| Token Type         | Example      |
| ------------------ | ------------ |
| Unquoted String    | foo          |
| Quoted String      | "foo"        |
| Regular Expression | /foo/        |
| Regular Token      | [{word:foo}] |
| Numeric Token      | [{index>10}] |
| Wildcard           | []           |
| Variable           | $FOO         |

### Token Sequence
The meat of the language comes from creating patterns over token sequences. This
follows traditional regex syntax, save that the atomic unit is a token rather
than a character. The quantifiers implemented are:

| Quantifier | Example   | Description |
| ---------- | --------- | ----------- |
| \|         | tabby? cat \| dog  | Matches either the pattern `tabby? cat` or the word `dog`. |
| &          | play & [{pos:NN}]+ | Matches both the pattern `play` or the pattern `[{pos:NN}]+`. Note that the length of the match for both patterns must be the same. In this case, both the left and right hand side expressions will match exactly 1 token |
| ?          | cat?      | Matches the pattern `cat` either once, or not at all. |
| ??         | cat??     | Matches the pattern `cat` reluctantly either once, or not at all. |
| *          | cat*      | Matches the pattern `cat` any number of times in a row, including not at all |
| *?         | cat*?     | Matches the pattern `cat` reluctantly any number of times in a row, including not at all |
| +          | cat+      | Matches the pattern `cat` any number of times in a row, but at least once |
| +?         | cat+?     | Matches the pattern `cat` reluctantly any number of times in a row, but at least once |
| {n}        | cat{3}    | Matches the pattern `cat` exactly 3 times |
| {n}?       | cat{3}?   | Matches the pattern `cat` exactly 3 times. This is identical to `{n}` |
| {n,}       | cat{3,}   | Matches the pattern `cat` at least 3 times |
| {n,}?      | cat{3,}?  | Matches the pattern `cat` reluctantly at least 3 times |
| {n,m}      | cat{3,5}  | Matches the pattern `cat` at least 3 times and at most 5 times |
| {n,m}?     | cat{3,5}? | Matches the pattern `cat` reluctantly at least 3 times and at most 5 times |

With this, we can now decompose and understand the pattern from the introduction:

```
[{ner:PERSON}]+ [{lemma:be}] born (on [!{pos:/VB.?/}])? in [{ner:LOCATION}]+
```

This will match one or more tokens with `PERSON` as the named entity
(`[{ner:PERSON}]+`), followed by a token with lemma "be" and a token with
default key (i.e., word) "born" (`[{lemma:be}] born`), followed optionally by a
prepositional phrase starting with "on" and no verb (`(on [!{pos:/VB.*/}])?`),
followed by "in" and one or more location tokens (`in [{ner:LOCATION}]+`).

### Capture Groups

A capture group is defined when a portion of a pattern is surrounded by
parentheses. For example, `([{pos:JJ}]) cat` would create a capture group around
the adjective in the pattern. Running the pattern against the input `black cat`,
you could retrieve the adjective `black` from the first capture group.

Capture groups can also be named. For example, `(?<type> [{pos:JJ}]) cat`
creates a capture group with name `type`. The name can be any string literal,
similar to unquoted elements in a pattern.

If a pattern's capture group captures multiple portions of a match, the last
span captured will be returned by the capture group.

### Differences from TokensRegex
The language differs in a few known ways from TokensRegex. These
are:
 
1. We don't implement multiple attribute matches (e.g., `[ { word:/cat|dog/;
   tag:"NN" } ]`) for a token. Instead. these must be represented as an explicit
   conjunction: `[ { word:/cat|dog/ } & { tag:"NN" } ]`). This is to simplify
   the grammar.
2. We don't implement boolean checks (e.g., `{ key::IS_NUM }`). This is to
   simplify the grammar, and avoid a proliferation of special-case functions.
   Note that null-checking is still supported with the syntax `[{!key}]`.
3. We don't support custom key aliases. For example:
   ```
   tokens = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation" }
   ```
   This is to simplify the grammar and usage of the system. Instead, users are
   encouraged to define their own implementation of `TRexInputToken` to handle 
   these custom keys. This is to simplify the grammar.
4. Braces are technically optional in T-Rex. So, `[{key:value}]` can
   equivalently be written as `[key:value]`. This is to make the grammar easier to
   read.
5. The recommended named capture group syntax is `(?<name> pattern)` rather than
   `(?$name pattern)` as in TokensRegex. Both are still parsed, but
   the former is more consistent with regular expression standards.

## Usage
The typical flow for using T-Rex is similar to that of the Java Regex
package. Compile a pattern, construct a Matcher, and run the matcher. For
example:

```
import com.squareup.trex.*;
...
Pattern p = Pattern.compile("[{pos:JJ}] cats");
Matcher m = p.matcher(input);
boolean match = m.matches();
```

We'll go over each of these stages below:

### Pattern compilation
A pattern is compiled with the `Pattern.compile()` static method. In its
simplest instantiation, it takes a string encoding the T-Rex pattern and
produces a `Pattern` object. In addition, compilation can take a number of extra
parameters:

1. **Case sensitivity**: Matches can either be case sensitive or case
   insensitive. They're case sensitive by default. Note that regular expressions
   within tokens are case sensitive always, and must be made case insensitive
   individually explicitly.
2. **Default key**: The default key can be overridden when compiling a pattern.
   This is the key used to look up token values when no explicit key is
   specified -- e.g., when the token is just a bare string. The default key is
   `default` by default.
3. **Variables**: A mapping from variable name to the associated variable
   pattern can be passed in during compilation, and will result in all instances
   of that variable being replaced by the pattern with the same name. This is a
   compile-time check, which means undefined variables will cause an exception
   to be thrown at compile-time.

A helper function exists to compile a set of variables. This effectively runs a
topological sort to determine which variables depend on other variables and
compile them in the appropriate order. The result can then be passed into the
`Pattern.compile` function. This helper function is:

```
Pattern#compileVariables(Map<String, String>) -> Map<String, Pattern>
```

### Creating a Matcher
A Matcher can be created from a compiled pattern. Multiple Matchers can be
created from a single Pattern, and can be run at the same time. Generally
speaking, a Matcher is created from a list of `TRexInputToken`s, where
`TRexInputToken` is a Map-like interface that can be implemented by the
end-user:

```
public interface TRexInputToken {
  @Nullable String get(String key);
}
```

A helper function exists in the case that we are matching CoreNLP tokens (i.e.,
`CoreLabel`s): `CoreNLPUtils#matcher`. This will take care of creating a list
of `CoreLabelInputToken` instances, which wrap a `CoreLabel` in the
`TRexInputToken` interface. `CoreLabelInputToken` defines the following
mappings from strings to `CoreAnnotation` objects:

| Key                | CoreAnnotation           |
| ------------------ | ------------------------ |
| default            | TextAnnotation           |
| word               | TextAnnotation           |
| text               | OriginalTextAnnotation   |
| pos                | PartOfSpeechAnnotation   |
| tag                | PartOfSpeechAnnotation   |
| lemma              | LemmaAnnotation          |
| ner                | NamedEntityTagAnnotation |
| normalized         | NormalizedNamedEntityTagAnnotation          |

Keys that are not in this mapping are assumed to be class names, and the
associated CoreAnnotation is looked up directly. For example,
"PolarityAnnotation" would map to
`edu.stanford.nlp.ling.CoreAnnotations.PolarityAnnotation`, as of course would
the fully qualified name of the class:
"edu.stanford.nlp.ling.CoreAnnotations.PolarityAnnotation".

### Matching Text
A matcher exposes two main methods for matching against text: `matches()` and
`find()`. `matches()` will attempt to match the entire input sequence from
beginning until end, and return true or false depending on whether the input
matches. In contrast, `find()` will search for the next index within the input
where a match was found, and return true if such an index could be found. The
span of the match can then be retrieved via the `start()` and `end()` methods on
the Matcher. A Matcher can always be reset mid-`find` with the `reset()`
function.

To prevent runaway queries, an optional timeout can be provided to both the
`matches()` and `find()` methods. This allows for defining an amount of time
(note: wall-time, not necessarily compute-time) that can elapse before aborting
the query and returning no match.

## Architecture
The code for T-Rex can be partitioned into 4 types of classes: (1) various
types of patterns that form the core of the T-Rex language; (2)
implementations of `TRexInputToken`; (3) the Matcher class, and (4) necessary
utilities. Each of these are described in detail below.

### Patterns
The `Pattern` interface (technically, abstract class) is the core interface for
matching a span of text. The necessary function to implement is:

```
protected abstract PrimitiveIterator.OfInt consume(
  List<? extends TRexInputToken> input,
  int index,
  Matcher context);
```

This takes as input a stream of tokens, the current index we're trying to match
at, and the context of our match. It produces as output an iterator of possible
end indices (inclusive) that this pattern has matched until. The translation
from the functions in `Matcher` to this is straightforward: `matches()` sill
simply check if any of the matches consume the entire input, and `find()` will
cycle through the iterator for all the spans returned, for each start index. In
turn, all of the different types of patterns implement this class. These break
down into single-token patterns (implementing `SingleTokenPattern`), and
multi-token patterns (implementing `MultiTokenPattern`). At a high level, most
quantifiers and other features of T-Rex have an associated `Pattern`
implementation.

The single token patterns generally take care of matching a portion of or an
entire token. For example, `StringPattern` is responsible for matching a single
key+value pair. Similarly `RegexPattern` matches a value with a given String
regex, `NegatedPattern` negates a match of its argument pattern, and so on.
These patterns can then be combined with operators using
`SingleTokenConjunctionPattern` and `SingleTokenDisjuctionPattern`. these take
as arguments two patterns, and create a new pattern that encodes the conjunction
or disjunction of the two.

Multi-token patterns are responsible for matching longer portions of text. This
includes the multi-token equivalents of conjunction and disjunction:
`ConjunctionPattern` and `DisjunctionPattern`. Much of the heavy lifting,
however, is done by `SequencePattern`. This is responsible for any sequence of
matches: this can be distinct patterns that need to be matched (e.g., `foo
bar`), or a single pattern repeated a number of times (e.g., `foo*` or
`foo{1,5}`, etc.). This is the class that's responsible for the core
backtracking search of the engine.

Many of the multi-token patterns implement a companion iterator class. This is
used to store the state for all of the possible matches for the pattern. For
example, a `SequencePattern` that repeats a given token may match multiple
lengths; the associated iterator class `SequencePatternIterator` is responsible
for keeping this state around and lazily returning all of the possible matches
for the pattern.

Detailed documentation on each of the Patterns can be found in their Javadoc.

### TRexInputToken
Currently, there's only a single implementation of a `TRexInputToken`, which is
for matching `CoreLabel` objects emitted from CoreNLP. Since the input token key
lookup is a very often called operation during pattern matching, care was taken
in the implementation to make this function fast. The named keys are cached at
construction time, the default key is checked with strict equality (`==`) before
checking against named keys with String equality, and care is taken to cache the
String-to-String map and avoid recomputation.

### Matcher
This class stores all of the transient state associated with a particular match
instance. This includes the necessary state for matching -- e.g., the next match
iterator -- but also metadata and utilities that are used by the patterns during
a match. For example, the timeout that the pattern uses to check whether the
deadline has been exceeded (see `Matcher#timeoutExceeded()`), or a function to
create a singleton iterator used by the patterns to avoid expensive object
allocation from trivial matches (see `Matcher#transientIterator()`).

### Utilities
The only utility function currently defined is the `SingleValueIterator` class.
Functionally, this is an iterator with either a single element or no elements.
It is used by Patterns which return just a single match (this is most Patterns).
To avoid excessive object creation, a singleton instance of the
`SingleValueIterator` class is stored in each matcher, and can be instantiated
during the matching process. Note that these iterators need to be immediately
discharged if used in this way, or else a new iterator will clobber the value of
the old one.

# License

```
Coyright 2021 Square, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```