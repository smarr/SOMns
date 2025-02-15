package bd.settings;

import static org.junit.Assert.assertFalse;

import org.junit.Test;


public class VmSettingsTests {

  @Test
  public void testDefaultValuesOfSettings() {
    assertFalse(VmSettings.DYNAMIC_METRICS);
  }
}
