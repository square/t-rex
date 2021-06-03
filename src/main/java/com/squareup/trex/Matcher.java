package com.squareup.trex;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Set;

/**
 * <p>
 *   A matcher for a given {@link Pattern} on a given token sequence, analogous
 *   to {@link java.util.regex.Matcher}.
 * </p>
 *
 * <p>
 *   A matcher is created from a pattern by invoking the pattern's {@link
 *   Pattern#matcher matcher} method. Note that for matching CoreNLP sentences,
 *   the helper {@link CoreNLPUtils#matcher(Pattern, List)} may be convenient.
 *   Once created, a matcher can be used to perform two different kinds
 *   of match operations:
 *
 * <ul>
 *   <li><p> The {@link #matches matches} method attempts to match the entire
 *   input sequence against the pattern.</p></li>
 *
 *   <li><p> The {@link #find find} method scans the input sequence looking
 *   for the next subsequence that matches the pattern. The matched bounds
 *   can then be inspected with {@link #start()} and {@link #end()}.</p></li>
 * </ul>
 *
 * <p> Each of these methods returns a boolean indicating success or failure.
 * More information about a successful match can be obtained by querying the
 * state of the matcher. </p>
 *
 * @param <T> The type of the tokens we're matching over. This is used when
 *            creating capture groups, so that the caller of the capture group
 *            can get properly typed tokens.
 *
 * @author <a href="mailto:gabor@squareup.com">Gabor Angeli</a>
 */
public class Matcher<T extends TRexInputToken> {

  /**
   * A class encapsulating a matched capture group. This is defined by
   * a start index and an end index of the matched span, though as a
   * convenience the token subspan match is also provided here.
   *
   * @param <T> The type of token we are matching, and therefore are
   *           returning in our token subspan match.
   */
  public static class CaptureGroup<T extends TRexInputToken> {
    /** 
     * The id of this capture group, corresponding to the match index from,
     * e.g., {@link #group(int)}
     */
    public final int id;
    /** The complete list of tokens we are matching. */
    private final List<T> tokens;
    /** The begin index of our match, inclusive. */
    private int beginInclusive;
    /** The end index of our match, exclusive. */
    private int endExclusive;

    /**
     * Create a capture group from an index span and the original token list.
     *
     * @param id See {@link #id}.
     * @param beginInclusive See {@link #beginInclusive}.
     * @param endExclusive See {@link #endExclusive}.
     * @param tokens See {@link #tokens}.
     */
    protected CaptureGroup(int id, int beginInclusive, int endExclusive, List<T> tokens) {
      this.id = id;
      this.beginInclusive = beginInclusive;
      this.endExclusive = endExclusive;
      this.tokens = tokens;
    }

    /**
     * Create a copy of the argument capture group.
     *
     * @param other The capture group we are acopying.
     */
    CaptureGroup(CaptureGroup<T> other) {
      this.id = other.id;
      this.beginInclusive = other.beginInclusive;
      this.endExclusive = other.endExclusive;
      this.tokens = other.tokens;
    }

    /**
     * @return The begin index of the matched group, inclusive.
     */
    public int getBeginInclusive() {
      return beginInclusive;
    }

    /**
     * @return The end index of the matched group, exclusive.
     */
    public int getEndExclusive() {
      return endExclusive;
    }

    /**
     * @return The list of matched tokens. This is the sublist of tokens from
     * the begin index to the end index of the match. This is an immutable
     * list.
     */
    public List<T> matchedTokens() {
      return Collections.unmodifiableList(tokens.subList(beginInclusive, endExclusive));
    }

    /** {@inheritDoc} */
    @SuppressWarnings("rawtypes")
    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CaptureGroup that = (CaptureGroup) o;
      return beginInclusive == that.beginInclusive &&
          endExclusive == that.endExclusive;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
      return Objects.hash(beginInclusive, endExclusive);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
      return "[" + beginInclusive + "," + endExclusive + ")";
    }
  }

  /**
   * The pattern we're using to match.
   */
  private final Pattern pattern;
  /**
   * The input we're matching against.
   */
  private final List<T> input;
  /**
   * The values of our capture groups, as spans begin+end indices.
   */
  private /* @Nullable */ Map<Pattern, CaptureGroup<T>> captureGroups = null;
  /**
   * Our named capture groups, keyed by name. Every capture group object in this
   * map must also be in the {@link #captureGroups} map. There may be multiple
   * capture groups for a given name (e.g., along different branches in the pattern),
   * in which case a name will map to all capture groups with that name.
   */
  private /* @Nullable */ Map<String, List<CaptureGroup<T>>> captureGroupNames = null;
  /**
   * A map storing all of our string capture groups. See
   * {@link #registerStringMatch(String, String)}.
   */
  private /* @Nullable */ Map<String, String> stringCaptureGroups = null;

  /**
   * The timestamp, in milliseconds, after which this matcher
   * should be considered delinquent and time out the match.
   * Note that this is wall-clock time; use a blocking GC at your
   * own peril.
   */
  private long timeoutMillis = Long.MAX_VALUE;

  /**
   * The iterator we are iterating through in the {@link #find(Duration)}
   * method.
   */
  private /* @Nullable */ PrimitiveIterator.OfInt iter = null;
  /**
   * The current index we're searching from, used as state for the
   * {@link #find(Duration)} method.
   */
  private int currentIndex = 0;
  /**
   * If we have a match from {@link #find(Duration)}, then that
   * match is stored here.
   */
  private /* @Nullable */ CaptureGroup<T> currentMatch = null;
  /**
   * The set of matches already found by the {@link #find(Duration)}
   * method, to avoid returning duplicate spans.
   */
  private final Set<CaptureGroup<T>> alreadyReturned = new HashSet<>();
  /**
   * The backing instance for {@link #transientIterator(int)}.
   * Note that this is not a static singleton, so that the method can remain threadsafe.
   */
  private final SingleValueIterator transientIterator = new SingleValueIterator();
  /**
   * A package-private boolean that can be used to signify a catchable error without
   * having to throw a full exception. This is an efficiency hack, as exceptions are
   * expensive to materialize.
   */
  boolean transientError = false;

  /**
   * Create a matcher from a pattern and a sequence to match.
   *
   * @param pattern @see #pattern
   * @param input @see #input
   */
  Matcher(Pattern pattern, List<T> input) {
    this.pattern = pattern;
    this.input = input;
    pattern.forEachComponent(component -> {
      if (component.captureGroupName != null) {
        // Ensure the mapping exists.
        // This is a pure memory optimization -- many patterns may not have
        // any capture groups defined.
        if (this.captureGroups == null) {
          this.captureGroups = new IdentityHashMap<>();
          this.captureGroupNames = new HashMap<>();
        }
        // Create the group
        CaptureGroup<T> group = new CaptureGroup<>(this.captureGroups.size() + 1,
            -1 /* begin */, -1 /* end */, input);
        // Register the group
        captureGroups.put(component, group);
        // Register the group by name, if it's a named group
        if (component.captureGroupName.length() > 0) {
          captureGroupNames
              .computeIfAbsent(component.captureGroupName, k -> new ArrayList<>())
              .add(group);
        }
      }
    });
  }

  /**
   * If true, we've exceeded our deadline for our matcher, and should
   * time out as soon as possible. This method is checked in the patterns'
   * {@link Pattern#consume(List, int, Matcher)} or
   * {@link Pattern#consume(List, int, Matcher)} methods at strategic
   * intervals to ensure that we don't continue computation for too long.
   * Note that this method is moderately expensive to call, amounting to
   * a few dozen nanoseconds (~24ns on Gabor's laptop).
   */
  boolean timeoutExceeded() {
    return System.currentTimeMillis() > timeoutMillis;
  }

  /**
   * Check if the pattern matches the entire input.
   *
   * @param timeLimit A time limit for running the matcher.
   *                  If this time limit is exceeded, a timeout exception is thrown.
   *
   * @return True if the pattern matches the entire input.
   */
  public boolean matches(Duration timeLimit) {
    resetCaptureGroups();
    PrimitiveIterator.OfInt iter = pattern.consume(input, 0, this);
    this.timeoutMillis = System.currentTimeMillis() + timeLimit.toMillis();
    while (iter.hasNext()) {
      if (iter.nextInt() == input.size()) {
        this.currentMatch = new CaptureGroup<>(0 /* id */, 0 /* begin */, input.size(), input);
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the pattern matches the entire input.
   *
   * @return True if the pattern matches the entire input.
   *
   * @see #matches(Duration)
   */
  public boolean matches() {
    return matches(Duration.ofHours(24));
  }

  /**
   * Find the next matching span.
   * The resulting span can be retrieved with {@link #start()} and {@link #end()}.
   * If this is called repeatedly, each subsequent matching span will be returned.
   *
   * @param timeLimit A time limit for running the matcher.
   *                  If this time limit is exceeded, a timeout exception is thrown.
   *
   * @return True if a new match was found.
   */
  public boolean find(Duration timeLimit) {
    resetCaptureGroups();
    long startTime = System.currentTimeMillis();
    this.timeoutMillis = startTime + timeLimit.toMillis();
    if (iter == null) {
      iter = pattern.consume(input, currentIndex, this);
    }

    if (iter.hasNext()) {
      int next = iter.nextInt();
      this.currentMatch = new CaptureGroup<>(0, this.currentIndex, next, input);
      if (this.alreadyReturned.contains(this.currentMatch)) {
        return find(timeLimit.minusMillis(System.currentTimeMillis() - startTime));
      } else {
        this.alreadyReturned.add(this.currentMatch);
        this.pattern.registerMatch(this.currentIndex, next, this);  // register final match
        return true;
      }
    } else if (currentIndex < this.input.size()) {
      resetIndex(currentIndex + 1);
      return find(timeLimit.minusMillis(System.currentTimeMillis() - startTime));
    } else {
      this.currentMatch = null;
      return false;
    }
  }

  /**
   * Find the next matching span.
   * The resulting span can be retrieved with {@link #start()} and {@link #end()}.
   * If this is called repeatedly, each subsequent matching span will be returned.
   *
   * @return True if a new match was found.
   *
   * @see #find(Duration)
   */
  public boolean find() {
    return find(Duration.ofHours(24));
  }

  /**
   * @return The start index, inclusive, of the last match, or a {@link NoSuchElementException}
   * is thrown if no find method was called or there are no more matches.
   */
  public int start() {
    if (this.currentMatch == null) {
      // Match exception from Java's standard library regex (but with more informative message)
      throw new IllegalStateException(
          "No match available. Did you run find() or matches() and ensure it returned true?");
    }
    return this.currentMatch.beginInclusive;
  }

  /**
   * @return The end index, exclusive, of the last match, or a {@link NoSuchElementException}
   * is thrown if no find method was called or there are no more matches.
   */
  public int end() {
    if (this.currentMatch == null) {
      // Match exception from Java's standard library regex (but with more informative message)
      throw new IllegalStateException(
          "No match available. Did you run find() or matches() and ensure it returned true?");
    }
    return this.currentMatch.endExclusive;
  }

  /**
   * <p>
   *   Recover the anonymous capture group with index |group|. This must be
   *   an index between 0 (denoting the whole pattern match) and the max
   *   number of capture groups. An exception is thrown if the index is out
   *   of bounds, and null is returned if the group at that index has no matches.
   * </p>
   *
   * <p>
   *   This is the analogue of {@link java.util.regex.Matcher#group(int)}.
   * </p>
   *
   * @param group The index of the capture group.
   *
   * @return The capture group, if one was found at that index, or null otherwise.
   *
   * @throws IndexOutOfBoundsException if the index is negative or greater than the number
   *                                   of capture groups.
   */
  public /* @Nullable */ CaptureGroup<T> group(int group) throws IndexOutOfBoundsException {
    if (currentMatch == null) {
      throw new IllegalStateException("No match found");
    } else if (group == 0) {
      return currentMatch;
    } else if (group < 0 || captureGroups == null || group > captureGroups.size()) {
      throw new IndexOutOfBoundsException(group);
    } else {
      for (CaptureGroup<T> capture : this.captureGroups.values()) {
        if (capture.id == group) {
          if (capture.beginInclusive >= 0) {
            return new CaptureGroup<>(capture);
          } else {
            return null;
          }
        }
      }
      return null;
    }
  }

  /**
   * <p>
   *   Recover the named capture group with name |name|. Null is returned
   *   if either no capture group exists with that name, or if we did not match
   *   that capture group.
   * </p>
   *
   * <p>
   *   This is the analogue of {@link java.util.regex.Matcher#group(String)}.
   * </p>
   *
   * @param name The name of the capture group.
   *
   * @return The capture group, if one was found with this name, or null otherwise.
   */
  public /* @Nullable */ CaptureGroup<T> group(String name) {
    if (currentMatch == null) {
      throw new IllegalStateException("No match found");
    } else if (captureGroupNames == null) {
      return null;
    } else {
      return this.captureGroupNames.getOrDefault(name, Collections.emptyList())
          .stream()
          .filter(group -> group != null && group.beginInclusive >= 0)
          .findFirst()
          .orElse(null);
    }
  }

  /**
   * Get all of the named capture group for the current match.
   *
   * @return A map from capture group name to the associated capture group object.
   */
  public Map<String, CaptureGroup<T>> namedCaptureGroups() {
    if (this.captureGroupNames == null) {
      return Collections.emptyMap();
    } else {
      Map<String, CaptureGroup<T>> rtn = new HashMap<>();
      for (Map.Entry<String, List<CaptureGroup<T>>> entry : this.captureGroupNames.entrySet()) {
        for (CaptureGroup<T> group : entry.getValue()) {
          if (group.getBeginInclusive() >= 0) {
            rtn.put(entry.getKey(), new CaptureGroup<>(group));
          }
        }
      }
      return rtn;
    }
  }

  /**
   * Returns the substring capture groups we matched along the way. Unlike
   * {@link #namedCaptureGroups()}, these groups are not matching entire tokens or token
   * sequences, but rather a substring of a single token. This is used to surface capture
   * groups caught by a {@linkplain RegexPattern regex embedded in a token pattern}.
   *
   * @return A map from capture group name to the value of the capture group -- i.e., the
   *         substring of the matching token that matched the capture group.
   */
  public Map<String, String> stringCaptureGroups() {
    if (this.stringCaptureGroups != null) {
      return new HashMap<>(this.stringCaptureGroups);
    } else {
      return Collections.emptyMap();
    }
  }

  /**
   * Reset our matcher.
   */
  public void reset() {
    resetIndex(0);
    resetCaptureGroups();
  }

  /**
   * Reset our matcher to start at a given index.
   */
  private void resetIndex(int index) {
    this.iter = null;  // invalidate the iterator
    this.currentIndex = index;
    this.alreadyReturned.clear();
  }

  /**
   * Resets all of the spans for the capture groups in this matcher.
   * This should be called before starting a new match that may have captures
   * in it.
   */
  private void resetCaptureGroups() {
    if (captureGroups != null) {
      for (CaptureGroup<T> capture : captureGroups.values()) {
        capture.beginInclusive = -1;
        capture.endExclusive = -1;
      }
    }
    if (stringCaptureGroups != null) {
      stringCaptureGroups.clear();
    }
  }

  /**
   * <p>
   *   Called by {@link Pattern#registerMatch(int, int, Matcher)}
   *   in order to register that a capture group matched a span.
   *   This updates {@link #captureGroups} and {@link #captureGroupNames}
   *   appropriately.
   * </p>
   *
   * <p>
   *   If there are multiple matches for the same named capture group, this
   *   will overwrite the most recent match.
   * </p>
   *
   * @param pattern The pattern that's encoding the capture group.
   * @param beginIndexInclusive The start of the match, inclusive.
   * @param endIndexExclusive The end of the match, exclusive.
   */
  void registerMatch(
      Pattern pattern,
      int beginIndexInclusive,
      int endIndexExclusive) {
    assert pattern.captureGroupName != null : "This pattern isn't a capturing pattern";
    assert this.captureGroups != null
        : "We shouldn't be registering a match for non-existent capture groups";
    CaptureGroup<T> capture = this.captureGroups.get(pattern);
    capture.beginInclusive = beginIndexInclusive;
    capture.endExclusive = endIndexExclusive;
  }

  /**
   * Register a pure String match. Unlike {@link #registerMatch(Pattern, int, int)}, which
   * matches a sequence of tokens, this is matching a substring of a token. The primary caller
   * of this method is {@link RegexPattern} in order to register capture groups in the regex
   * itself.
   *
   * @param key The capture group's key.
   * @param value The matched value.
   */
  void registerStringMatch(String key, String value) {
    if (this.stringCaptureGroups == null) {
      this.stringCaptureGroups = new HashMap<>();
    }
    this.stringCaptureGroups.put(key, value);
  }


  /**
   * <p>
   *   <b>DO NOT USE THIS METHOD UNLESS YOU KNOW WHAT YOU'RE DOING.</b>
   * </p>
   *
   * <p>
   *   This returns a singleton instance of a single valued iterator,
   *   with the given value loaded in. This means that the iterator must
   *   be discharged before another call to this method is made, or else the
   *   two iterators will clobber each other. This iterator will be global
   *   to this matcher, though every matcher will have its own instance.
   * </p>
   *
   * @param value The value that forms the single value the iterator will return.
   *
   * @return A global singleton iterator, primed with the argument value.
   */
  SingleValueIterator transientIterator(int value) {
    assert !transientIterator.hasNext : "Iterator is being set but was never discharged";
    if (value >= 0) {
      transientIterator.value = value;
      transientIterator.hasNext = true;
    }
    return transientIterator;
  }
}
