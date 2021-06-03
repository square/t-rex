package com.squareup.trex;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 *   A {@link TRexInputToken} that wraps a CoreLabel. This is functionally
 *   a wrapper around {@link CoreLabel#get(Class)}, but (1) caches results
 *   locally to avoid expensive lookups, (2) converts from string keys
 *   required by TokensRegex into the class keys required by CoreLabels,
 *   and (3) adds some useful aliases for common keys, similar to the
 *   original TokensRegex.
 * </p>
 *
 * <p>
 *   The {@linkplain Pattern#DEFAULT_KEY default key} returns the
 *   {@linkplain CoreLabel#word() word} in the CoreLabel.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class CoreLabelInputToken implements TRexInputToken {

  /**
   * The Logger for this class
   */
  private static final Logger log = LoggerFactory.getLogger(CoreLabelInputToken.class);

  /**
   * The backing CoreLabel for this input token.
   */
  public final CoreLabel token;

  /** The word at this token. See {@link CoreLabel#word()}. */
  private final String word;
  /** The original text at this token. See {@link CoreLabel#originalText()}. */
  private final String originalText;
  /** The part of speech tag at this token. See {@link CoreLabel#tag()}. */
  private final String pos;
  /** The lemma at this token. See {@link CoreLabel#lemma()}. */
  private final String lemma;
  /** The named entity tag at this token. See {@link CoreLabel#ner()}. */
  private final String ner;
  /** The normalized named entity tag at this token. This is here for backwards compatibility. */
  private final String normalizedNer;
  /**
   * A cached version of all the keys in the CoreLabel, mapped from both their fully
   * qualified class name as well as their simple class name. This is null at construction
   * time to cut down on the compute time and memory required, since arbitrary keys are
   * rarely used.
   */
  @Nullable private Map<String, String> values = null;

  /**
   * Create an input token wrapping the given CoreLabel
   *
   * @param token The underlying CoreLabel to use for matching against this token.
   */
  public CoreLabelInputToken(CoreLabel token) {
    this.token = token;
    this.word = token.word();
    this.originalText = token.originalText();
    this.pos = token.tag();
    this.lemma = token.lemma();
    this.ner = token.ner();
    this.normalizedNer = token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
  }

  /**
   * Recompute the cached values in {@link #values}.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private void populateValues() {
    values = new HashMap<>();
    for (Class key : token.keySet()) {
      values.put(key.getSimpleName().toLowerCase(), token.get(key).toString());
      values.put(key.getName().toLowerCase(), token.get(key).toString());
    }
  }

  /** {@inheritDoc} */
  @Override public String get(String key) {
    // Short circuit if we're the common case to avoid the .equals()
    // check implicit in the switch statement. Note that we still check for
    // real equality there, so the '==' is OK for this quick check
    //noinspection StringEquality
    if (key == Pattern.DEFAULT_KEY) {
      return word;
    }

    // Switch over named keys
    switch (key) {
      case Pattern.DEFAULT_KEY:
      case "word":
      case "WORD":
        return word;
      case "text":
      case "TEXT":
        return originalText;
      case "pos":
      case "POS":
      case "tag":
      case "TAG":
        return pos;
      case "lemma":
      case "LEMMA":
        return lemma;
      case "ner":
      case "NER":
        return ner;
      case "normalized":
      case "NORMALIZED":
        return normalizedNer;
      default:
        if (values == null) {
          populateValues();
        }
        String value = values.get(key.toLowerCase());
        if (value == null) {
          log.warn("[[Could not find value in CoreLabel for key {}]]", key);
        }
        return value;
    }
  }
}
