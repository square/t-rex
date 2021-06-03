package com.squareup.trex;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A small utility class to help with common NLP annotation tasks in the unit tests.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class NLPUtils {

  /**
   * The Logger for this class
   */
  private static final Logger log = LoggerFactory.getLogger(NLPUtils.class);

  private static final Map<Properties, StanfordCoreNLP> pipelines = new ConcurrentHashMap<>();

  /**
   * Tokenize a sentence with CoreNLP, yielding the associated CoreNLP Document (i.e.,
   * Annotation) object.
   *
   * @param raw The raw text to annotate.
   * @param singleSentence If true, force this to be a single sentence
   *
   * @return The CoreNLP annotated document.
   */
  public static Annotation corenlpTokenize(String raw, boolean singleSentence) {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit");
    props.setProperty("tokenize.language", "en");
    props.setProperty("tokenize.options", "splitHyphenated=true,invertible,ptb3Escaping=true");
    if (singleSentence) {
      props.setProperty("ssplit.isOneSentence", "true");
    }

    if (!pipelines.containsKey(props))
      pipelines.put(props, new StanfordCoreNLP(props));

    StanfordCoreNLP pipeline = pipelines.get(props);
    Annotation ann = new Annotation(raw);
    pipeline.annotate(ann);
    return ann;
  }

  /**
   * A small utility to tokenize.
   *
   * @param raw The text that we are tokenizing
   *
   * @return The resulting tokens.
   */
  public static List<List<CoreLabel>> tokenize(String raw) {
    Annotation ann = corenlpTokenize(raw, false);
    List<List<CoreLabel>> sentences = new ArrayList<>(ann.get(CoreAnnotations.SentencesAnnotation.class).size());
    for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreLabel> tokens = new ArrayList<>(sentence.get(CoreAnnotations.TokensAnnotation.class).size());
      tokens.addAll(sentence.get(CoreAnnotations.TokensAnnotation.class));
      sentences.add(tokens);
    }
    return sentences;
  }
}
