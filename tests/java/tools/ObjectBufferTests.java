package tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Test;


public class ObjectBufferTests {

  @Test
  public void testBasicStoreAndRead() {
    final int size = 10;
    ObjectBuffer<Integer> buffer = new ObjectBuffer<>(size);

    assertTrue(buffer.isEmpty());
    assertEquals(0, buffer.size());
    assertEquals(0, buffer.capacity());

    for (int i = 0; i < 128; i++) {
      buffer.append(i);
      assertFalse(buffer.isEmpty());
      assertEquals(i + 1, buffer.size());

      int nextUpperMultipleOfSize = (int) (size * Math.ceil((i + 1) / (double) size));
      assertEquals(nextUpperMultipleOfSize, buffer.capacity());
    }

    int expectedI = 0;
    for (int i : buffer) {
      assertEquals(expectedI, i);
      expectedI += 1;
    }
  }

  @Test
  public void testEmpty() {
    ObjectBuffer<Integer> buffer = new ObjectBuffer<>(10);

    assertTrue(buffer.isEmpty());

    Iterator<Integer> i = buffer.iterator();
    assertFalse(i.hasNext());

    try {
      i.next();
      fail("Should throw exception");
    } catch (NoSuchElementException e) { }

    assertEquals(0, buffer.size());
    assertEquals(0, buffer.capacity());
  }

  @Test
  public void testOneElementFull() {
    ObjectBuffer<Integer> buffer = new ObjectBuffer<>(1);
    buffer.append(1234);

    Iterator<Integer> i = buffer.iterator();
    assertTrue(i.hasNext());
    assertEquals(1234, (int) i.next());
    assertFalse(i.hasNext());

    try {
      i.next();
      fail("Should throw exception");
    } catch (NoSuchElementException e) { }

    assertEquals(1, buffer.size());
    assertEquals(1, buffer.capacity());
  }
}
