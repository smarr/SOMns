package bd.settings;

/**
 * VMs using Black Diamonds need to provide a class that implements this interface to provide
 * basic configuration information.
 */
public interface Settings {
  boolean dynamicMetricsEnabled();
}
