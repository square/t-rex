package com.squareup.trex;

import edu.stanford.nlp.ling.CoreLabel;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A set of utility functions for using patterns with CoreNLP. This is broken
 * out so that CoreNLP is not strictly necessary in the runtime classpath in order
 * to use the library.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class CoreNLPUtils {

  /**
   * A helper for {@link Pattern#matcher(List)} for matching a list of CoreNLP tokens.
   *
   * @param pattern The pattern to create a matcher against. See {@link Pattern#matcher(List)}
   * @param sentence A list of CoreLabels, representing a sentence, or other span of text
   *                 to match against.
   *
   * @return A matcher for the argument pattern matching against the argument sentence.
   */
  public static Matcher<CoreLabelInputToken> matcher(Pattern pattern, List<CoreLabel> sentence) {
    List<CoreLabelInputToken> input = sentence.stream()
        .map(CoreLabelInputToken::new).collect(Collectors.toList());
    return pattern.matcher(input);
  }
}
