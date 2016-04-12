package tools;

import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * Simple buffer class to efficiently record objects with minimal possible
 * overhead.
 */
public class ObjectBuffer<T> implements Iterable<T> {

  private final int bufferSize;

  private Entry<T> current;
  private Entry<T> first;
  private int currentIdx;

  private int numEntries;

  @SuppressWarnings("unchecked")
  private static class Entry<T> {
    private final T[] buffer;
    private Entry<T> next;

    Entry(final int bufferSize, final Entry<T> prev) {
      buffer = (T[]) new Object[bufferSize];
      this.next = null;

      if (prev != null) {
        prev.next = this;
      }
    }
  }

  public ObjectBuffer(final int bufferSize) {
    this.bufferSize = bufferSize;
    this.currentIdx = bufferSize;
    this.numEntries = 0;
  }

  public void append(final T item) {
    if (currentIdx >= bufferSize) {
      currentIdx = 0;
      numEntries += 1;
      current = new Entry<>(bufferSize, current);

      if (first == null) {
        first = current;
      }
    }

    current.buffer[currentIdx] = item;
    currentIdx += 1;
  }

  public boolean isEmpty() {
    return current == null;
  }

  public int size() {
    if (numEntries == 0) {
      return 0;
    }
    return ((numEntries - 1) * bufferSize) + currentIdx;
  }

  public int capacity() {
    return numEntries * bufferSize;
  }

  @Override
  public Iterator<T> iterator() {
    return new Iter<T>(currentIdx, first);
  }

  private static final class Iter<T> implements Iterator<T> {

    private final int lastIdxInLastEntry;
    private Entry<T> current;
    private int currentIdx;

    private Iter(final int lastIdx, final Entry<T> current) {
      this.lastIdxInLastEntry = lastIdx - 1;
      this.current = current;
      this.currentIdx = 0;
    }

    @Override
    public boolean hasNext() {
      if (current == null) {  // empty, had never any element
        return false;
      }
      if (current.next == null) {
        return currentIdx <= lastIdxInLastEntry;
      }
      return true;
    }

    @Override
    public T next() {
      if (current == null || (current.next == null && currentIdx > lastIdxInLastEntry)) {
        throw new NoSuchElementException();
      }

      if (currentIdx >= current.buffer.length) {
        current = current.next;
        currentIdx = 0;
      }

      T result = current.buffer[currentIdx];
      currentIdx += 1;
      return result;
    }
  }
}
