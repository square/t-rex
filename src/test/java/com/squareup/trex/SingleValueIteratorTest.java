package com.squareup.trex;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A simple unit test for {@link SingleValueIterator}.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
class SingleValueIteratorTest {

  /**
   * Create an empty iterator and ensure it's empty
   */
  @Test void createEmpty() {
    SingleValueIterator empty = new SingleValueIterator();
    assertFalse(empty.hasNext());
    assertFalse(SingleValueIterator.EMPTY.hasNext());
  }

  /**
   * Create an "full" iterator and ensure it has a single element.
   */
  @Test void createFull() {
    SingleValueIterator iter = new SingleValueIterator(42);
    assertTrue(iter.hasNext());
    assertEquals(42, iter.nextInt());
    assertFalse(iter.hasNext());
  }

  /**
   * Ensure that the iterator can't return integers once its empty
   */
  @Test void cantOverflow() {
    SingleValueIterator iter = new SingleValueIterator(42);
    assertTrue(iter.hasNext());
    assertEquals(42, iter.nextInt());
    assertFalse(iter.hasNext());
    assertThrows(NoSuchElementException.class, iter::nextInt);
    assertThrows(NoSuchElementException.class, SingleValueIterator.EMPTY::nextInt);
  }

}