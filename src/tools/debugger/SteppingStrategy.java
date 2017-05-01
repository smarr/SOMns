package tools.debugger;


public abstract class SteppingStrategy {

  protected boolean consumed;

  public SteppingStrategy() {
    consumed = false;
  }

  /** Return true if the spawned activity should stop on its root node. */
  public boolean handleSpawn() { return false; }
  public void prepareExecution(final WebDebugger dbg) { }

  public static final class IntoSpawn extends SteppingStrategy {
    @Override
    public boolean handleSpawn() {
      if (consumed) { return false; } else { consumed = true; }
      return true;
    }
  }
}
