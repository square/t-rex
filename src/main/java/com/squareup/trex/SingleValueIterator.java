package com.squareup.trex;

import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

/**
 * <p>
 *   This class implements a simple int iterator that can encode
 *   either 0 or 1 elements. This is primarily here for the unsafe
 *   method {@link Matcher#transientIterator(int)} that allows reusing a single
 *   instance of this class to return a single value wrapped in an iterator
 *   without having to incur the object creation overhead of a real iterator.
 * </p>
 *
 * <p>
 *   This class should not be used outside of this package.
 * </p>
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
/* do not make public */ class SingleValueIterator implements PrimitiveIterator.OfInt {

  /**
   * The value encoded in this iterator.
   */
  int value;
  /**
   * If true, we have not yet consumed this value.
   */
  boolean hasNext;

  /**
   * Create an iterator with a single value.
   */
  SingleValueIterator(int value) {
    this.hasNext = true;
    this.value = value;
  }

  /**
   * Create an empty iterator
   */
  SingleValueIterator() {
    this.value = -1;
    this.hasNext = false;
  }

  /** {@inheritDoc} */
  @Override public boolean hasNext() {
    return hasNext;
  }

  /** {@inheritDoc} */
  @Override public int nextInt() {
    if (hasNext) {
      hasNext = false;
      return value;
    } else {
      throw new NoSuchElementException();
    }
  }

  /**
   * An empty iterator. This will never have a value, and simply returns
   * false from {@link #hasNext()} on every invocation.
   */
  static final SingleValueIterator EMPTY = new SingleValueIterator();

}
