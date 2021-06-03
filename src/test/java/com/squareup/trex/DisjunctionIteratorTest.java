package com.squareup.trex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test the utility functions in the disjunction iterator.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class DisjunctionIteratorTest {

  /**
   * Test the already returned function on a simple input.
   */
  @Test void alreadyReturnedSimpleTest() {
    DisjunctionIterator iter = new DisjunctionIterator(null, null, 0, null, null);
    assertFalse(iter.checkAndUpdateAlreadyReturned(42));
    assertTrue(iter.checkAndUpdateAlreadyReturned(42));
  }

  /**
   * Test the already returned function on lengths that fit into a long
   */
  @Test void alreadyReturnedSmallLength() {
    DisjunctionIterator iter = new DisjunctionIterator(null, null, 0, null, null);
    for (int i = 0; i < 64; ++i) {
      assertFalse(iter.checkAndUpdateAlreadyReturned(i));
      assertTrue(iter.checkAndUpdateAlreadyReturned(i));
    }
  }

  /**
   * Test the already returned function on lengths that don't fit into
   * a long.
   */
  @Test void alreadyReturnedLargeLength() {
    DisjunctionIterator iter = new DisjunctionIterator(null, null, 0, null, null);
    for (int i = 64; i < 100; ++i) {
      assertFalse(iter.checkAndUpdateAlreadyReturned(i));
      assertTrue(iter.checkAndUpdateAlreadyReturned(i));
    }
  }

  /**
   * Test the already returned function first on lengths that fit into a long,
   * and then check that we migrate correctly over to using a full bitset
   * for lengths that don't fit into a long.
   */
  @Test void alreadyReturnedBothLengths() {
    DisjunctionIterator iter = new DisjunctionIterator(null, null, 0, null, null);
    for (int i = 0; i < 100; ++i) {
      assertFalse(iter.checkAndUpdateAlreadyReturned(i));
      assertTrue(iter.checkAndUpdateAlreadyReturned(i));
    }
    for (int i = 0; i < 100; ++i) {
      assertTrue(iter.checkAndUpdateAlreadyReturned(i));
    }
  }
}
