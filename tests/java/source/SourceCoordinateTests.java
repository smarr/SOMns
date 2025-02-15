package source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import bd.source.SourceCoordinate;


public class SourceCoordinateTests {

  @Test
  public void testBasicEqualsHash() {
    SourceCoordinate a = SourceCoordinate.create("file:///", 1, 2, 2, 4);
    SourceCoordinate b = SourceCoordinate.create("file:///", 1, 2, 2, 4);

    assertNotSame(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    assertTrue(a.equals(b));
  }

  @Test
  public void testEqualsHashWithUnsetCharIndex() {
    SourceCoordinate a = SourceCoordinate.create("file:///", 1, 2, 2, 4);

    // underlying source might not be loaded, should still match
    SourceCoordinate b = SourceCoordinate.create("file:///", 1, 2, 4);

    assertNotSame(a, b);
    assertEquals(a.hashCode(), b.hashCode());

    assertTrue(a.equals(b));
  }
}
