package tools.debugger;


public abstract class SteppingStrategy {

  protected boolean consumed;

  public SteppingStrategy() {
    consumed = false;
  }

  /** Return true if the spawned activity should stop on its root node. */
  public boolean handleSpawn() { return false; }
  public boolean handleChannelMessage() { return false; }

  // TODO: can I convert that into a simple location enum, or even Tag check???
  public static final class IntoSpawn extends SteppingStrategy {
    @Override
    public boolean handleSpawn() {
      if (consumed) { return false; } else { consumed = true; }
      return true;
    }
  }

  public static final class ToChannelOpposite extends SteppingStrategy {
    @Override
    public boolean handleChannelMessage() {
      if (consumed) { return false; } else { consumed = true; }
      return true;
    }
  }
}
