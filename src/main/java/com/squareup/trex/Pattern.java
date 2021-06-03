package com.squareup.trex;

import static com.squareup.trex.TRexParser.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.function.Consumer;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * <p>
 *   The Antlr visitor for generating a {@link Pattern}. This works
 *   in conjunction with {@link SingleTokenPatternVisitor}, which is the
 *   visitor for parsing single-token expressions and their components,
 *   and with {@link ValueVisitor}, which is a small visitor class for
 *   parsing the root value types (e.g., regex, quoted string, unquoted
 *   string) in a pattern.
 * </p>
 *
 * <p>
 *   The usual root entry point for this visitor are the various branches
 *   of the |pattern| rule: either a list of atoms, a conjunction of patterns,
 *   or a disjunction of patterns.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class PatternVisitor extends TRexBaseVisitor<Pattern> {

  /**
   * The pattern we are visiting, as originally input.
   * This will not have whitespace and comments stripped.
   */
  private final String originalPattern;
  /**
   * The parser instance, used for debugging output / exceptions.
   */
  private final TRexParser parser;
  /**
   * The set of variables we have at our disposal.
   */
  private final Map<String, Pattern> variables;
  /**
   * A visitor for parsing a single token.
   */
  private final SingleTokenPatternVisitor singleTokenVisitor;

  /**
   * Create a new pattern visitor. We need to create one of these
   * every time we parse a new pattern.
   *
   * @param originalPattern The pattern we are parsing. This is for error messages.
   * @param parser The parser. This is for error messages.
   * @param caseSensitive If true, our matches should be case-sensitive.
   * @param defaultKey The default lookup key for bare values. That is, what field
   *                   in a {@link TRexInputToken} should we look up if we're parsing
   *                   just a regular string (e.g., "foo")?
   * @param variables The variables we have at our disposal when parsing this pattern.
   */
  PatternVisitor(
      String originalPattern,
      TRexParser parser,
      boolean caseSensitive,
      String defaultKey,
      Map<String, Pattern> variables) {
    this.originalPattern = originalPattern;
    this.parser = parser;
    this.variables = variables;
    this.singleTokenVisitor
      = new SingleTokenPatternVisitor(originalPattern, parser, caseSensitive, defaultKey);
  }

  /**
   * Generate a human-readable exception for what went wrong when
   * parsing our pattern.
   */
  private PatternSyntaxException mkException(
      ParserRuleContext ctx,
      String cause) {
    String message = cause + " @ "
        + ctx.getText() + ": " + ctx.toInfoString(parser);
    return new PatternSyntaxException(
        message,
        originalPattern,
        ctx.start.getStartIndex());
  }

  /**
   * A small utility function to parse a list of atoms into a Pattern.
   * This is used in most of the top level rules, which allow for multiple
   * atoms in a row.
   *
   * @param atoms The list of atoms we are parsing.
   *
   * @return The parsed pattern for these atoms. This is either a
   *         {@link SequencePattern} or a simple pattern, if there was
   *         only one atom.
   */
  private Pattern parseAtomList(List<AtomContext> atoms) {
    List<Pattern> components = atoms.stream()
        .map(x -> x.accept(this))
        .collect(Collectors.toList());
    if (components.size() == 1) {
      return components.get(0);
    } else {
      return SequencePattern.sequence(components);
    }
  }

  /** {@inheritDoc} */
  @Override public Pattern visitAtom_list(Atom_listContext ctx) {
    return parseAtomList(ctx.atom());
  }

  /** {@inheritDoc} */
  @Override public Pattern visitConjunction(ConjunctionContext ctx) {
    Pattern lhs = parseAtomList(ctx.atom());
    Pattern rhs = ctx.pattern().accept(this);
    if (lhs instanceof SingleTokenPattern && rhs instanceof SingleTokenPattern &&
        // Length test required to account for 0-length patterns.
        ((SingleTokenPattern) lhs).length() == ((SingleTokenPattern) rhs).length()) {
      return new SingleTokenConjunctionPattern(
          (SingleTokenPattern) lhs, (SingleTokenPattern) rhs);
    } else {
      return new ConjunctionPattern(lhs, rhs);
    }
  }

  /** {@inheritDoc} */
  @Override public Pattern visitDisjunction(DisjunctionContext ctx) {
    Pattern lhs = parseAtomList(ctx.atom());
    Pattern rhs = ctx.pattern().accept(this);
    if (lhs instanceof SingleTokenPattern && rhs instanceof SingleTokenPattern &&
        // Length test required to account for 0-length patterns.
        ((SingleTokenPattern) lhs).length() == ((SingleTokenPattern) rhs).length()) {
      return new SingleTokenDisjunctionPattern(
          (SingleTokenPattern) lhs, (SingleTokenPattern) rhs);
    } else {
      return new DisjunctionPattern(lhs, rhs);
    }
  }

  /** {@inheritDoc} */
  @Override public Pattern visitVariable_atom(Variable_atomContext ctx) {
    String name = ctx.Variable().getText().substring(1);
    Pattern value = this.variables.get(name);
    if (value == null) {
      throw mkException(ctx, "Could not find variable '" + name + "' in context. "
          + "Has it been declared already?");
    }
    return value;
  }

  /** {@inheritDoc} */
  @Override public Pattern visitParen_atom(Paren_atomContext ctx) {
    return ctx.parenthetical().accept(this);
  }

  /** {@inheritDoc} */
  @Override public Pattern visitToken_atom(Token_atomContext ctx) {
    // Promote a single token pattern to a pattern
    return ctx.token().accept(singleTokenVisitor);
  }

  /** {@inheritDoc} */
  @Override public Pattern visitStar_atom(Star_atomContext ctx) {
    Pattern atom = ctx.atom().accept(this);
    boolean reluctant = false;
    if (ctx.QMark() != null) {
      reluctant = true;
    }
    return SequencePattern.star(atom, reluctant);
  }

  /** {@inheritDoc} */
  @Override public Pattern visitPlus_atom(Plus_atomContext ctx) {
    Pattern atom = ctx.atom().accept(this);
    boolean reluctant = false;
    if (ctx.QMark() != null) {
      reluctant = true;
    }
    return SequencePattern.plus(atom, reluctant);
  }
  /** {@inheritDoc} */
  @Override public Pattern visitQmark_atom(Qmark_atomContext ctx) {
    Pattern atom = ctx.atom().accept(this);
    boolean reluctant = false;
    if (ctx.QMark(1) != null) {
      reluctant = true;
    }
    return SequencePattern.qmark(atom, reluctant);
  }

  /** {@inheritDoc} */
  @Override public Pattern visitRepeat_atom_bounded(Repeat_atom_boundedContext ctx) {
    Pattern atom = ctx.atom().accept(this);
    int lowerBound;
    int upperBound;
    try {
      lowerBound = Integer.parseInt(ctx.Number(0).getText().replace("--", ""));
    } catch (NumberFormatException e) {
      throw mkException(ctx, "Could not parse lower bound");
    }
    try {
      upperBound = Integer.parseInt(ctx.Number(1).getText().replace("--", ""));
    } catch (NumberFormatException e) {
      throw mkException(ctx, "Could not parse upper bound");
    }
    if (lowerBound < 0) {
      throw mkException(ctx, "Lower bound cannot be negative");
    }
    if (upperBound < 0) {
      throw mkException(ctx, "Upper bound cannot be negative");
    }
    if (upperBound < lowerBound) {
      throw mkException(ctx, "Upper bound cannot be less than the lower bound");
    }
    boolean reluctant = false;
    if (ctx.QMark() != null) {
      reluctant = true;
    }
    return new SequencePattern(atom, null, lowerBound, upperBound, reluctant);
  }

  /** {@inheritDoc} */
  @Override public Pattern visitRepeat_atom_unbounded(Repeat_atom_unboundedContext ctx) {
    Pattern atom = ctx.atom().accept(this);
    int lowerBound;
    try {
      lowerBound = Integer.parseInt(ctx.Number().getText().replace("--", ""));
    } catch (NumberFormatException e) {
      throw mkException(ctx, "Could not parse lower bound");
    }
    if (lowerBound < 0) {
      throw mkException(ctx, "Lower bound cannot be negative");
    }
    boolean reluctant = false;
    if (ctx.QMark() != null) {
      reluctant = true;
    }
    return new SequencePattern(atom, null, lowerBound, Integer.MAX_VALUE, reluctant);
  }

  /** {@inheritDoc} */
  @Override public Pattern visitRepeat_atom_exact(Repeat_atom_exactContext ctx) {
    Pattern atom = ctx.atom().accept(this);
    int count;
    try {
      count = Integer.parseInt(ctx.Number().getText().replace("--", ""));
    } catch (NumberFormatException e) {
      throw mkException(ctx, "Could not parse lower bound");
    }
    if (count < 0) {
      throw mkException(ctx, "repeat count cannot be negative");
    }
    return new SequencePattern(atom, null, count, count, false /* is reluctant */);
  }

  /** {@inheritDoc} */
  @Override public Pattern visitAnonymous_parenthetical(Anonymous_parentheticalContext ctx) {
    return ctx.pattern().accept(this).setCaptureGroup("" /* name */);
  }

  /** {@inheritDoc} */
  @Override public Pattern visitNamed_parenthetical(Named_parentheticalContext ctx) {
    String name = ctx.UnquotedStringLiteral().getText();
    return ctx.pattern().accept(this).setCaptureGroup(name);
  }

  /** {@inheritDoc} */
  @Override public Pattern visitLegacy_named_parenthetical(Legacy_named_parentheticalContext ctx) {
    String name = ctx.Variable().getText().substring(1);  // strip the leading '$'
    return ctx.pattern().accept(this).setCaptureGroup(name);
  }
}

/**
 * A visitor that produces a {@link SingleTokenPattern}. This is
 * distinct from {@link PatternVisitor} to ensure the invariant that single
 * token boolean expressions (&, |, !) are only operating on single tokens.
 * This avoids having to cast implicitly in the visitor.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class SingleTokenPatternVisitor
    extends TRexBaseVisitor<SingleTokenPattern> {

  /**
   * If true, we should be matching the casing on strings.
   */
  private final boolean caseSensitive;
  /**
   * A visitor for a single value. This has the default lookup key
   * baked in -- i.e., the lookup key to use when we encounter bare
   * values in the pattern.
   */
  private final ValueVisitor defaultValueVisitor;
  /**
   * The pattern we are visiting, as originally input.
   * This will not have whitespace and comments stripped.
   */
  private final String originalPattern;
  /**
   * The parser instance, used for debugging output / exceptions.
   */
  private final TRexParser parser;

  /**
   * Generate a human-readable exception for what went wrong when
   * parsing our pattern.
   */
  private PatternSyntaxException mkException(
      ParserRuleContext ctx,
      String cause) {
    String message = cause + " @ "
        + ctx.getText() + (parser == null ? "" : (": " + ctx.toInfoString(parser)));
    return new PatternSyntaxException(
        message,
        originalPattern,
        ctx.start.getStartIndex());
  }

  /**
   * Create a new visitor for parsing a single token pattern.
   * @param originalPattern See {@link #originalPattern}
   * @param parser See {@link #parser}
   * @param caseSensitive See {@link #caseSensitive}
   * @param defaultKey The lookup key to use when we encounter a bare value.
   */
  SingleTokenPatternVisitor(String originalPattern, TRexParser parser, boolean caseSensitive,
      String defaultKey) {
    this.caseSensitive = caseSensitive;
    this.originalPattern = originalPattern;
    this.parser = parser;
    this.defaultValueVisitor = new ValueVisitor(defaultKey, caseSensitive);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitLiteral_token(Literal_tokenContext ctx) {
    return ctx.value().accept(defaultValueVisitor);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitComplex_token(Complex_tokenContext ctx) {
    return ctx.token_body().accept(this);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitStart_token(Start_tokenContext ctx) {
    return SequenceBoundaryPattern.CARET;
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitEnd_token(End_tokenContext ctx) {
    return SequenceBoundaryPattern.DOLLAR;
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitWildcard_token(Wildcard_tokenContext ctx) {
    return WildcardPattern.INSTANCE;
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitKeyword_token(Keyword_tokenContext ctx) {
    String text = ctx.getText();
    return new StringPattern(this.defaultValueVisitor.key, text, this.caseSensitive);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitToken_body_simple(Token_body_simpleContext ctx) {
    return ctx.token_body_atom().accept(this);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitToken_body_conjunction(
      Token_body_conjunctionContext ctx) {
    SingleTokenPattern lhs = ctx.token_body().accept(this);
    SingleTokenPattern rhs = ctx.token_body_atom().accept(this);
    return new SingleTokenConjunctionPattern(lhs, rhs);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitToken_body_disjunction(
      Token_body_disjunctionContext ctx) {
    SingleTokenPattern lhs = ctx.token_body().accept(this);
    SingleTokenPattern rhs = ctx.token_body_atom().accept(this);
    return new SingleTokenDisjunctionPattern(lhs, rhs);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitToken_body_atom_simple(
      Token_body_atom_simpleContext ctx) {
    return ctx.key_value_pair().accept(this);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitToken_body_atom_paren(
      Token_body_atom_parenContext ctx) {
    return ctx.token_body().accept(this);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitToken_body_atom_negated(
      Token_body_atom_negatedContext ctx) {
    return new NegatedPattern(ctx.token_body_atom().accept(this));
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitKey_value_pair(Key_value_pairContext ctx) {
    return ctx.braceless_key_value_pair().accept(this);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitKey_value_string(Key_value_stringContext ctx) {
    String key = ctx.UnquotedStringLiteral().getText();
    return ctx.value().accept(new ValueVisitor(key, this.caseSensitive));
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitKey_value_numeric(Key_value_numericContext ctx) {
    String key = ctx.UnquotedStringLiteral().getText();
    try {
      int value = Integer.parseInt(ctx.Number().getText());
      NumericPattern.Operator op;
      switch (ctx.numeric_op().getText()) {
        case "<":
          op = NumericPattern.Operator.LT;
          break;
        case ">":
          op = NumericPattern.Operator.GT;
          break;
        case "<=":
          op = NumericPattern.Operator.LTE;
          break;
        case ">=":
          op = NumericPattern.Operator.GTE;
          break;
        case "==":
        case "=":
          op = NumericPattern.Operator.EQ;
          break;
        case "!=":
          op = NumericPattern.Operator.NEQ;
          break;
        default:
          throw mkException(ctx, "Invalid operator: " + ctx.numeric_op().getText() + ".");
      }
      return new NumericPattern(key, value, op);
    } catch (NumberFormatException e) {
      throw mkException(ctx, "Numeric comparison of non-numeric value: " + ctx.Number().getText());
    }
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitKey_value_null(Key_value_nullContext ctx) {
    String key = ctx.UnquotedStringLiteral().getText();
    return new NullPattern(key);
  }

}

/**
 * A small visitor to determine what we do when we encounter
 * a value that we need to parse into a pattern.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class ValueVisitor
    extends TRexBaseVisitor<SingleTokenPattern> {

  /**
   * The lookup key for this value.
   */
  final String key;
  /**
   * If true, we should match the value including case.
   */
  private final boolean caseSensitive;

  /**
   * Creates a new value visitor with the given information.
   *
   * @param key @see #key
   * @param caseSensitive @see #value
   */
  ValueVisitor(String key, boolean caseSensitive) {
    this.key = key;
    this.caseSensitive = caseSensitive;
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitValue_unquoted(Value_unquotedContext ctx) {
    String text = ctx.getText();
    return new StringPattern(this.key, text, this.caseSensitive);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitValue_quoted(Value_quotedContext ctx) {
    String text = ctx.QuotedStringLiteral().getText();
    text = text.substring(1, text.length() - 1);
    return new StringPattern(this.key, text, this.caseSensitive);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitValue_regex(Value_regexContext ctx) {
    String text = ctx.RegexLiteral().getText();
    text = text.substring(1, text.length() - 1);
    if (text.matches("[^\\\\\\[\\]^$&|{}?*.+]+")) {
      // This is safely not really a regex as it doesn't contain any of the
      // regex special characters. create a simple string matcher for it
      return new StringPattern(this.key, text, this.caseSensitive);
    } else {
      int flags = 0;
      // If we contain non-ascii characters in our regex, make sure we
      // are running in Unicode mode
      if (!text.matches("\\A\\p{ASCII}*\\z")) {
        flags |= java.util.regex.Pattern.UNICODE_CASE;
      }
      // If the entire pattern is meant to be case-insensitive, make sure the
      // component regular expressions are as well
      if (!caseSensitive) {
        flags |= java.util.regex.Pattern.CASE_INSENSITIVE;
      }
      java.util.regex.Pattern regex = java.util.regex.Pattern.compile(text, flags);
      return new RegexPattern(this.key, regex);
    }
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitValue_number(Value_numberContext ctx) {
    String text = ctx.Number().getText().replace("--", "");
    if ("-0".equals(text)) {
      text = "0";
    }
    return new StringPattern(this.key, text, this.caseSensitive);
  }

  /** {@inheritDoc} */
  @Override public SingleTokenPattern visitValue_comma(Value_commaContext ctx) {
    String text = ctx.getText();
    return new StringPattern(this.key, text, this.caseSensitive);
  }
}

/**
 * <p>
 *   A token sequence (i.e., TRex) pattern, analogous to {@link java.util.regex.Pattern}.
 *   This is a regular expression, specified as a string, must first be compiled into
 *   an instance of this class.  The resulting pattern can then be used to create
 *   a {@link Matcher} object that can match arbitrary token sequences against the regular
 *   expression.  All of the state involved in performing a match resides in the
 *   matcher, so many matchers can share the same pattern.
 * </p>
 *
 * <p>
 *   A typical invocation sequence is thus
 * </p>
 *
 * <blockquote><pre>
 * Pattern p = Pattern.{@link #compile compile}("[{pos:DT}] cat");
 * Matcher m = p.{@link #matcher matcher}(sentence);
 * boolean b = m.{@link Matcher#matches matches}();
 * </pre></blockquote>
 *
 * <p>
 *   A summary of the TRex grammar
 *   <a href="https://nlp.stanford.edu/software/tokensregex.shtml">can be found here</a>.
 *   This re-implementation is feature complete with the exception of:
 * </p>
 *
 * <ol>
 *   <li>Numerics; e.g., <pre>{key==number}</pre></li>
 *   <li>Boolean checks; e.g., <pre>{key:IS_NUM}</pre></li>
 *   <li>Reluctant quantifiers; e.g., <pre>X*?</pre></li>
 *   <li>Capture groups; e.g.,  <pre>capture (this)</pre></li>
 *   <li>The JSON patterns language to create TRex files</li>
 * </ol>
 *
 * <p>
 *   The key design motivations for this class and its implementing subclasses are:
 * </p>
 *
 * <ol>
 *   <li>Efficiency. The implementation has been designed with efficiency in mind, to
 *       try to minimize the amount of compute required to match patterns.</li>
 *   <li>Minimize garbage. The implementation tries to minimize produced object garbage
 *       to make life easier on the GC and reduce memory spikes.</li>
 *   <li>Allow interrupts. The implementation includes timeouts as a first-class concept
 *       to avoid runaway pattern matches.</li>
 * </ol>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public abstract class Pattern {

  /**
   * The default lookup key for a {@link TRexInputToken}.
   * This is used if no key is provided explicitly when compiling the pattern
   * and we have bare values in the pattern.
   */
  static final String DEFAULT_KEY = "default";

  /** A special pattern that consumes no input. This can be a singleton. */
  private static final Pattern EMPTY = new Pattern() {
    /** {@inheritDoc} */
    @Override
    protected PrimitiveIterator.OfInt consume(
        List<? extends TRexInputToken> input, int index,
        Matcher<? extends TRexInputToken> context) {
      return context.transientIterator(index);
    }

    /** {@inheritDoc} */
    @Override protected void populateToString(StringBuilder b) {}

    /** {@inheritDoc} */
    @Override public String toString() {
      return "";
    }
  };

  /**
   * The name of this capture group. There are three possible values for this:
   * <ol>
   *   <li>A null value implies that this is not a capture group.</li>
   *   <li>An empty string implies that this is an anonymous (unnamed) capture group.</li>
   *   <li>A non-empty string implies that this is a named capture group.</li>
   * </ol>
   */
  /* @Nullable */ String captureGroupName = null;

  /**
   * The main implementing method of a pattern. This returns an iterator of all of the end
   * spans that this pattern can match until, given the starting index specified in the
   * |index| parameter. Note that this method is allowed and encouraged to return a
   * {@linkplain Matcher#transientIterator(int) transient iterator}, and therefore the
   * iterator returned from this should be immediately discharged if it can be. If an iterator
   * has more than one element, it is guaranteed to not be transient.
   *
   * @param input The input we are matching, as a list of tokens.
   * @param index The index we are beginning our match from.
   * @param context The matcher that is performing this match. This is used primarily
   *                for capture groups, and for {@linkplain Matcher#timeoutExceeded() timeouts}.
   *
   * @return An iterator -- possibly transient -- of all the possible matches for this pattern.
   */
  protected abstract PrimitiveIterator.OfInt consume(
      List<? extends TRexInputToken> input,
      int index,
      Matcher<? extends TRexInputToken> context);

  /**
   * <p>
   *   Visit each component of this pattern, in depth-first order.
   *   For example, the pattern /(a b) c/ would visit [a, b, (a b), c, (a b) c]
   *   in that order. In the case of compound tokens (e.g., [{a:b} | {b:c}],
   *   all elements that are visited. So, both {a:b} and {b:c} will be visited.
   *   For repeated patterns (e.g., a+), both the component pattern (i.e., a) and
   *   the repeated construct (i.e., a+) will be visited.
   * </p>
   *
   * <p>
   *   The default implementation of this function simply visits
   *   the current pattern. If this is an insufficient implementation
   *   then this function must be overridden.
   * </p>
   *
   * @param fn The function called for each component visited.
   */
  protected void forEachComponent(Consumer<Pattern> fn) {
    fn.accept(this);
  }

  /**
   * <p>
   *   Generate the internal contents of this pattern. This does not
   *   include capture groups or the token boundary markers ('[' and ']'),
   *   which are generated in the {@link #toString()} function.
   * </p>
   *
   * <p>
   *   In most cases, this is the function we will call recursively
   *   to generate a string representation of a pattern. In some cases,
   *   this will recursively call into {@link #toString()}.
   * </p>
   *
   * @param b The string builder we're appending to.
   */
  protected abstract void populateToString(StringBuilder b);

  /** {@inheritDoc} */
  public String toString() {
    StringBuilder str = new StringBuilder();
    if (this.captureGroupName != null) {
      str.append('(');
      if (this.captureGroupName.length() > 0) {
        str.append('?')
            .append('<')
            .append(this.captureGroupName)
            .append('>')
            .append(' ');
      }
    }
    if (this instanceof SingleTokenPattern && ((SingleTokenPattern) this).length() == 1) {
      str.append('[');
      populateToString(str);
      str.append(']');
    } else {
      populateToString(str);
    }
    if (this.captureGroupName != null) {
      str.append(')');
    }
    return str.toString();
  }

  /**
   * Mutably set the capture group for this pattern.
   *
   * @param name An name for this capture group. This can be the empty string if this is
   *             an unnamed capture group.
   *
   * @return This same pattern.
   */
  protected Pattern setCaptureGroup(String name) {
    this.captureGroupName = name;
    return this;
  }

  /**
   * Register that this pattern has matched a subset of text.
   * This takes care of setting the capture groups appropriately in the matcher,
   * if this is a capturing group. If not, this is a noop.
   *
   * @param beginIndexInclusive The begin index of the match, inclusive.
   * @param endIndexExclusive The end index of the match, exclusive.
   * @param context The matcher we are using during this run of the regex.
   */
  protected void registerMatch(
      int beginIndexInclusive,
      int endIndexExclusive,
      Matcher<? extends TRexInputToken> context) {
    if (this.captureGroupName != null) {
      context.registerMatch(this, beginIndexInclusive, endIndexExclusive);
    }
  }

  /**
   * Create a matcher for this pattern, matching against a given input sequence.
   * Note that this matcher stores all the state for the match, and therefore multiple
   * matchers can be made for the same pattern.
   *
   * @param <T> The type of token to match against; e.g., a
   *            {@link CoreLabelInputToken} or {@link MapTRexInputToken}.
   * @param input The input to match against.
   *
   * @return A matcher for this pattern against the given input sequence.
   */
  public <T extends TRexInputToken> Matcher<T> matcher(List<T> input) {
    return new Matcher<>(this, input);
  }

  /**
   * Compile a pattern from its string specification.
   *
   * @param pattern The pattern we are compiling, as a string to be parsed.
   * @param caseSensitive If true, matches must be case sensitive.
   * @param defaultKey The default lookup key to use when a key is not specified for a token.
   * @param variables The variables we can reference in the pattern.
   *                  See {@link #compileVariables(Map, boolean)} for a convenient way to compile
   *                  a list of variables, resolving dependencies appropriately.
   *
   * @return The compiled pattern.
   *
   * @throws PatternSyntaxException Thrown if the pattern could not be parsed, or is invalid
   *         and could not be compiled.
   */
  public static Pattern compile(
      String pattern,
      boolean caseSensitive,
      String defaultKey,
      Map<String, Pattern> variables) throws PatternSyntaxException {
    if (pattern.trim().equals("")) {
      // Short circuit on the empty pattern
      return Pattern.EMPTY;
    } else {
      // Run the parser
      TRexLexer lexer = new TRexLexer(CharStreams.fromString(pattern));
      // We remove error listeners because Antlr tends to generate line-noise logs and we throw
      // exceptions below anyways.
      lexer.removeErrorListeners();
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      TRexParser parser = new TRexParser(tokens);
      parser.removeErrorListeners();  // as above: avoid line noise
      TRexParser.PatternContext eval = parser.pattern();
      if (parser.getNumberOfSyntaxErrors() > 0 || eval.exception != null) {
        throw new IllegalArgumentException("Invalid pattern: " + pattern, eval.exception);
      }
      PatternVisitor visitor
          = new PatternVisitor(pattern, parser, caseSensitive, defaultKey, variables);
      return visitor.visit(eval);
    }
  }

  /**
   * Compiles a pattern with a default key of 'default'.
   *
   * @param pattern The pattern we are compiling, as a string to be parsed.
   * @param caseSensitive If true, matches must be case sensitive.
   * @param variables The variables we can reference in the pattern.
   *                  See {@link #compileVariables(Map, boolean)} for a convenient way to compile
   *                  a list of variables, resolving dependencies appropriately.
   *
   * @return The compiled pattern.
   *
   * @see #compile(String, boolean, String, Map)
   */
  public static Pattern compile(
      String pattern,
      boolean caseSensitive,
      Map<String, Pattern> variables) throws PatternSyntaxException {
    return compile(pattern, caseSensitive, DEFAULT_KEY, variables);
  }

  /**
   * Compiles a pattern with no variables.
   *
   * @param pattern The pattern we are compiling, as a string to be parsed.
   * @param caseSensitive If true, matches must be case sensitive.
   * @param defaultKey The default lookup key to use when a key is not specified for a token.
   *
   * @return The compiled pattern.
   *
   * @see #compile(String, boolean, String, Map)
   */
  public static Pattern compile(
      String pattern,
      boolean caseSensitive,
      String defaultKey) throws PatternSyntaxException {
    return compile(pattern, caseSensitive, defaultKey, Collections.emptyMap());

  }

  /**
   * Compiles a pattern, with a default key of 'default', and
   * no variables.
   *
   * @param pattern The pattern we are compiling, as a string to be parsed.
   * @param caseSensitive If true, matches must be case sensitive.
   *
   * @return The compiled pattern.
   *
   * @see #compile(String, boolean, String, Map)
   */
  public static Pattern compile(
      String pattern,
      boolean caseSensitive) {
    return compile(pattern, caseSensitive, DEFAULT_KEY, Collections.emptyMap());
  }

  /**
   * Compiles a pattern, case sensitive, with a default key of 'default', and
   * no variables.
   *
   * @param pattern The pattern we are compiling, as a string to be parsed.
   *
   * @return The compiled pattern.
   *
   * @see #compile(String, boolean, String, Map)
   */
  public static Pattern compile(String pattern) {
    return compile(pattern, true, DEFAULT_KEY, Collections.emptyMap());
  }

  /**
   * Compiles a collection of variables, where the variables can reference other
   * variables in the set. This effectively topologically sorts the patterns, compiling
   * them in an order such that all variable dependencies are met. Note that the variables
   * cannot have circular dependencies.
   *
   * @param variables The variables we are compiling, keyed on their variable name and with
   *                  the uncompiled pattern as a value.
   * @param caseSensitive If true, compile the variables so that the matches are case sensitive.
   * @param defaultKey The default lookup key to use for bare values with the patterns.
   *
   * @return A mapping from variable names to compiled patterns corresponding to that variable.
   *
   * @throws PatternSyntaxException Thrown if we could not compile a pattern
   */
  public static Map<String, Pattern> compileVariables(
      Map<String, String> variables,
      boolean caseSensitive,
      String defaultKey) throws PatternSyntaxException {
    Map<String, Pattern> compiledVariables = new HashMap<>();

    // Topologically sort the variable dependencies
    List<Map.Entry<String, String>> fringe = new ArrayList<>(variables.entrySet());
    boolean madeProgress = true;
    while (!fringe.isEmpty() && madeProgress) {
      madeProgress = false;
      Iterator<Map.Entry<String, String>> iter = fringe.iterator();
      while (iter.hasNext()) {
        // Clean the name + pattern
        Map.Entry<String, String> var = iter.next();
        String name = var.getKey();
        if (name.startsWith("$")) {
          name = name.substring(1);
        }
        String pattern = var.getValue();
        // Try to parse the variable with the current env
        try {
          compiledVariables.put(
              name,
              Pattern.compile(pattern, caseSensitive, defaultKey, compiledVariables));
          // Remove this variable from the fringe
          iter.remove();
          madeProgress = true;
        } catch (PatternSyntaxException t) {
          // ... otherwise, leave it in the fringe for the next pass.
        }
      }
    }

    // Check for compilation errors
    if (!fringe.isEmpty()) {
      throw new PatternSyntaxException(
          "Could not compile " + fringe.size() + " patterns. First failure:",
          fringe.iterator().next().getValue(), 0);
    }

    // Return
    return compiledVariables;
  }

  /**
   * Compiles a collection of variables, where the variables can reference other
   * variables in the set. This effectively topologically sorts the patterns, compiling
   * them in an order such that all variable dependencies are met. Note that the variables
   * cannot have circular dependencies.
   *
   * @param variables The variables we are compiling, keyed on their variable name and with
   *                  the uncompiled pattern as a value.
   * @param caseSensitive If true, compile the variables so that the matches are case sensitive.
   *
   * @return A mapping from variable names to compiled patterns corresponding to that variable.
   *
   * @see #compileVariables(Map, boolean, String)
   */
  public static Map<String, Pattern> compileVariables(
      Map<String, String> variables,
      boolean caseSensitive) throws PatternSyntaxException {
    return compileVariables(variables, caseSensitive, DEFAULT_KEY);
  }
}
