package som.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import som.VmOptions;
import som.VmSettings;


public class VMTests {
  @BeforeClass
  public static void enableInstrumentation() {
    System.setProperty(VmSettings.INSTRUMENTATION_PROP, "true");
  }

  @Test
  public void testProcessArgumentsNothing() {
    VmOptions opts = new VmOptions(new String[0]);
    assertEquals(opts.platformFile, VmOptions.STANDARD_PLATFORM_FILE);
    assertArrayEquals(opts.args, new String[0]);
  }

  @Test
  public void testProcessArgumentsWithPlatformFile() {
    VmOptions opts = new VmOptions(
        new String[] {"--platform", "foo.som"});
    assertEquals(opts.platformFile, "foo.som");
    assertArrayEquals(opts.args, new String[0]);
  }

  @Test
  public void testProcessArgumentsWithKernelFile() {
    VmOptions opts = new VmOptions(
        new String[] {"--kernel", "foo.som"});
    assertEquals(opts.kernelFile, "foo.som");
    assertArrayEquals(opts.args, new String[0]);
  }

  @Test
  public void testProcessArgumentsWithAppFile() {
    VmOptions opts = new VmOptions(
        new String[] {"app.som"});
    assertEquals(opts.platformFile, VmOptions.STANDARD_PLATFORM_FILE);
    assertArrayEquals(opts.args, new String[] {"app.som"});
  }

  @Test
  public void testProcessArgumentsWithAppFileAndArgs() {
    VmOptions opts = new VmOptions(
        new String[] {"app.som", "Foo", "1", "2"});
    assertEquals(opts.platformFile, VmOptions.STANDARD_PLATFORM_FILE);
    assertArrayEquals(opts.args, new String[] {"app.som", "Foo", "1", "2"});
  }

  @Test
  public void testProfileFlag() {
    VmOptions opts = new VmOptions(
        new String[] {"--profile"});
    assertEquals(opts.platformFile, VmOptions.STANDARD_PLATFORM_FILE);
    assertTrue(opts.profilingEnabled);
    assertArrayEquals(opts.args, new String[0]);
  }

  @Test
  public void testDebugFlag() {
    VmOptions opts = new VmOptions(
        new String[] {"--debug"});
    assertEquals(opts.platformFile, VmOptions.STANDARD_PLATFORM_FILE);
    assertTrue(opts.debuggerEnabled);
    assertFalse(opts.profilingEnabled);
    assertArrayEquals(opts.args, new String[0]);
  }
}
