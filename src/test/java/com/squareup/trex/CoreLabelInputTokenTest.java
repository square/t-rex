package com.squareup.trex;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test a {@link CoreLabelInputToken}.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class CoreLabelInputTokenTest {

  private static final CoreLabelInputToken LABEL = new CoreLabelInputToken(new CoreLabel(){{
    set(CoreAnnotations.OriginalTextAnnotation.class, "cats");
    set(CoreAnnotations.TextAnnotation.class, "-cats-");
    set(CoreAnnotations.PartOfSpeechAnnotation.class, "NNS");
    set(CoreAnnotations.NamedEntityTagAnnotation.class, "O");
    set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, "OO");
    set(CoreAnnotations.LemmaAnnotation.class, "cat");
    set(CoreAnnotations.BeginIndexAnnotation.class, 0);
    set(CoreAnnotations.EndIndexAnnotation.class, 1);
    set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, 0);
    set(CoreAnnotations.CharacterOffsetEndAnnotation.class, 4);
  }});

  /**
   * Test getting a field of a core label token by its alias
   */
  @Test void testDefaultField() {
    assertEquals("-cats-", LABEL.get(Pattern.DEFAULT_KEY));
  }

  /**
   * Test getting a field of a core label token by its alias
   */
  @Test void testGetNamedField() {
    assertEquals("cats", LABEL.get("text"));
    assertEquals("-cats-", LABEL.get("word"));
    assertEquals("NNS", LABEL.get("pos"));
    assertEquals("NNS", LABEL.get("tag"));
    assertEquals("O", LABEL.get("ner"));
    assertEquals("OO", LABEL.get("normalized"));
    assertEquals("cat", LABEL.get("lemma"));
  }

  /**
   * Test getting a field of a core label token by its alias, upper-cased
   */
  @Test void testGetNamedFieldUppercase() {
    assertEquals("cats", LABEL.get("TEXT"));
    assertEquals("-cats-", LABEL.get("WORD"));
    assertEquals("NNS", LABEL.get("POS"));
    assertEquals("NNS", LABEL.get("TAG"));
    assertEquals("O", LABEL.get("NER"));
    assertEquals("OO", LABEL.get("NORMALIZED"));
    assertEquals("cat", LABEL.get("LEMMA"));
  }

  /**
   * Test getting a field of a core label token by the literal
   * annotation name.
   */
  @Test void testGetUnnamedField() {
    assertEquals("cat", LABEL.get("LemmaAnnotation"));
    assertEquals("0", LABEL.get("BeginIndexAnnotation"));
    assertEquals("cat", LABEL.get(
        CoreAnnotations.LemmaAnnotation.class.getName()));
  }

}