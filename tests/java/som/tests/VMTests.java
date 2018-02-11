package som.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import som.vm.VmOptions;
import som.vm.VmSettings;


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
        new String[] {"--platform", "foo.ns"});
    assertEquals(opts.platformFile, "foo.ns");
    assertArrayEquals(opts.args, new String[0]);
  }

  @Test
  public void testProcessArgumentsWithKernelFile() {
    VmOptions opts = new VmOptions(
        new String[] {"--kernel", "foo.ns"});
    assertEquals(opts.kernelFile, "foo.ns");
    assertArrayEquals(opts.args, new String[0]);
  }

  @Test
  public void testProcessArgumentsWithAppFile() {
    VmOptions opts = new VmOptions(
        new String[] {"app.ns"});
    assertEquals(opts.platformFile, VmOptions.STANDARD_PLATFORM_FILE);
    assertArrayEquals(opts.args, new String[] {"app.ns"});
  }

  @Test
  public void testProcessArgumentsWithAppFileAndArgs() {
    VmOptions opts = new VmOptions(
        new String[] {"app.ns", "Foo", "1", "2"});
    assertEquals(opts.platformFile, VmOptions.STANDARD_PLATFORM_FILE);
    assertArrayEquals(opts.args, new String[] {"app.ns", "Foo", "1", "2"});
  }

  @Test
  public void testProfileFlag() {
    try {
      VmOptions opts = new VmOptions(
          new String[] {"--profile"});
      assertEquals(opts.platformFile, VmOptions.STANDARD_PLATFORM_FILE);
      assertTrue(opts.profilingEnabled);
      assertArrayEquals(opts.args, new String[0]);
    } catch (IllegalStateException e) {
      assertTrue("If it fails, expect that it complains that instrumentation is not enabled",
          e.getMessage().contains(VmSettings.INSTRUMENTATION_PROP));
    }
  }
}
