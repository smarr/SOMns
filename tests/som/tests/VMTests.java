package som.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import som.VMOptions;


public class VMTests {

  @Test
  public void testProcessArgumentsNothing() {
    VMOptions opts = new VMOptions(new String[0]);
    assertEquals(opts.platformFile, VMOptions.STANDARD_PLATFORM_FILE);
    assertNull(opts.args);
  }

  @Test
  public void testProcessArgumentsWithPlatformFile() {
    VMOptions opts = new VMOptions(
        new String[] {"--platform", "foo.som"});
    assertEquals(opts.platformFile, "foo.som");
    assertNull(opts.args);
  }

  @Test
  public void testProcessArgumentsWithKernelFile() {
    VMOptions opts = new VMOptions(
        new String[] {"--kernel", "foo.som"});
    assertEquals(opts.kernelFile, "foo.som");
    assertNull(opts.args);
  }

  @Test
  public void testProcessArgumentsWithAppFile() {
    VMOptions opts = new VMOptions(
        new String[] {"app.som"});
    assertEquals(opts.platformFile, VMOptions.STANDARD_PLATFORM_FILE);
    assertArrayEquals(opts.args, new String[] {"app.som"});
  }

  @Test
  public void testProcessArgumentsWithAppFileAndArgs() {
    VMOptions opts = new VMOptions(
        new String[] {"app.som", "Foo", "1", "2"});
    assertEquals(opts.platformFile, VMOptions.STANDARD_PLATFORM_FILE);
    assertArrayEquals(opts.args, new String[] {"app.som", "Foo", "1", "2"});
  }
}
