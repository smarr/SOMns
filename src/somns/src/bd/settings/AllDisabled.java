package bd.settings;

public class AllDisabled implements Settings {

  @Override
  public boolean dynamicMetricsEnabled() {
    return false;
  }
}
