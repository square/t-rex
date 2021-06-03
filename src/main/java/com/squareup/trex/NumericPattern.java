package com.squareup.trex;

import java.util.List;

/**
 * This is a pattern implementation for doing numeric comparisons against
 * the value at a given key. This is for tokens like <pre>[{index&gt;=4}]</pre>.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class NumericPattern extends SingleTokenPattern {

  /**
   * <p>
   *   The type of numeric operator. This determines the comparison we are making
   *   between the value in the pattern and the value in the input token.
   * </p>
   *
   * <p>
   *   <b>NOTE</b> Please update all callers of this enum if a new constant is added.
   * </p>
   */
  enum Operator {
    /** less than */
    LT,
    /** greater than */
    GT,
    /** greater than or equal to */
    GTE,
    /** less than or equal to */
    LTE,
    /** equal to */
    EQ,
    /** not equal to */
    NEQ,
    ;
  }

  /**
   * The lookup key for the value we are matching.
   */
  public final String key;

  /**
   * The target value we are comparing the value at the specified key against
   */
  public final int value;

  /**
   * The operator we are using for the comparison. For example, equality or
   * less than, etc.
   */
  public final Operator op;

  /**
   * Create a new numeric comparator pattern.
   *
   * @param key See {@link #key}
   * @param value See {@link #value}
   * @param op See {@link #op}
   */
  public NumericPattern(String key, int value, Operator op) {
    this.key = key;
    this.value = value;
    this.op = op;
  }

  /**
   * <p>
   *   A custom implementation of {@link Integer#parseInt(String)} aimed to be
   *   (1) more efficient parsing of integers, and (2) more importantly not throw a
   *   {@link NumberFormatException}, which is expensive to create.
   * </p>

   * <p>
   *   This function takes roughly 80% as long as {@link Integer#parseInt(String)}.
   *   On Gabor's Macbook Pro in 2019, this took 12ns compared to 15ns for the
   *   stdlib version.
   * </p>
   *
   * @param str A string representing an integer value.
   * @param context The matcher we are using as context for this match. Used to store
   *                our error value.
   *
   * @return The integer value corresponding to the string. If the string could
   *         not be parsed, {@link Matcher#transientError} is set in the input
   *         matcher, and a value of 0 is returned.
   */
  static int parseInt(String str, Matcher<? extends TRexInputToken> context) {
    context.transientError = false;  // start in the error-less state

    // Parse the sign
    int characterIndex = 0;
    int sign = 1;
    int length = str.length();
    if (str.charAt(0) == '-') {
      if (length == 1) {  // can't have just '-' as an integer
        context.transientError = true;
        return 0;
      }
      characterIndex = 1;
      sign = -1;
    }

    // Parse the value
    int value = 0;
    for (; characterIndex < length; ++characterIndex) {
      char c = str.charAt(characterIndex);
      if (c < '0' || c > '9') {
        context.transientError = true;
        return 0;
      } else {
        value = value * 10 + (c - '0');
        if (value < 0) {  // we overflowed our integer
          // There's a stupid special case where we are parsing Integer.MIN_VALUE,
          // which overflows from the positive side (because the limit values are not
          // symmetric) but is nonetheless a valid number to parse. This check is for
          // that case.
          if (sign == -1 && value == Integer.MIN_VALUE && characterIndex == length - 1) {
            return Integer.MIN_VALUE;
          }
          context.transientError = true;
          return 0;
        }
      }
    }

    // Return
    return value * sign;
  }

  /** {@inheritDoc} */
  @Override
  protected boolean matches(List<? extends TRexInputToken> input, int index,
      Matcher<? extends TRexInputToken> context) {
    // Check that we are in bounds
    if (index < 0 || index >= input.size()) {
      return false;
    }

    // Parse our value
    String actual = input.get(index).get(key);
    if (actual == null) {
      return false;
    }
    int actualInt = parseInt(actual, context);
    if (context.transientError) {
      context.transientError = false;  // clear the error
      return false;
    }

    // Run our comparison
    switch (op) {
      case LT:
        return actualInt < this.value;
      case GT:
        return actualInt > this.value;
      case LTE:
        return actualInt <= this.value;
      case GTE:
        return actualInt >= this.value;
      case NEQ:
        return actualInt != this.value;
      default:
      case EQ:
        return actualInt == this.value;
    }
  }

  /** {@inheritDoc} */
  @Override protected void populateToString(StringBuilder b) {
    b.append(key);
    switch (op) {
      case LT:
        b.append('<');
        break;
      case GT:
        b.append('>');
        break;
      case GTE:
        b.append(">=");
        break;
      case LTE:
        b.append("<=");
        break;
      case NEQ:
        b.append("!=");
        break;
      default:
      case EQ:
        b.append("==");
        break;
    }
    b.append(value);
  }
}
