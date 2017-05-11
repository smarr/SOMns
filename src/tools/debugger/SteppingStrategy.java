package tools.debugger;

import som.vm.Activity;
import som.vm.ActivityThread;
import som.vm.VmSettings;

public abstract class SteppingStrategy {

  protected boolean consumed;

  public SteppingStrategy() {
    consumed = false;
  }

  /** Return true if the spawned activity should stop on its root node. */
  public boolean handleSpawn() { return false; }
  public boolean handleChannelMessage() { return false; }
  public boolean handleTx() { return false; }
  public boolean handleTxCommit() { return false; }
  public boolean handleTxAfterCommit() { return false; }

  public void handleResumeExecution(final Activity activity) { }

  public boolean handleMessageReceiver() { return false; }
  public boolean handleMessageToPromiseResolution() { return false; }
  public boolean handleMessageReturnFromTurn() { return false; }

  public void handleMessageToNextTurn(final Activity activity) { }

  // TODO: can we get rid of the code doing the checking for those stepping things,
  //       and integrate it into the breakpoint checking nodes?
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

  public static final class ReturnFromActivity extends SteppingStrategy {
    @Override
    public void handleResumeExecution(final Activity activity) {
      if (consumed) { return; } else { consumed = true; }

      activity.setStepToJoin(true);
    }
  }

  public static final class ToNextTransaction extends SteppingStrategy {
    @Override
    public boolean handleTx() {
      if (consumed) { return false; } else { consumed = true; }

      return true;
    }
  }

  public static final class ToNextCommit extends SteppingStrategy {
    @Override
    public boolean handleTxCommit() {
      if (consumed) { return false; } else { consumed = true; }

      return true;
    }
  }

  public static final class AfterCommit extends SteppingStrategy {
    @Override
    public boolean handleTxAfterCommit() {
      if (consumed) { return false; } else { consumed = true; }

      return true;
    }
  }

  public static final class ToMessageReceiver extends SteppingStrategy {
    @Override
    public boolean handleMessageReceiver() {
        if (consumed) { return false; } else { consumed = true; }
        return true;
    }
  }

  public static final class ToPromiseResolution extends SteppingStrategy {
    @Override
    public boolean handleMessageToPromiseResolution() {
        if (consumed) { return false; } else { consumed = true; }
        return true;
    }
  }

  public static final class ToNextTurn extends SteppingStrategy {
    @Override
    public void handleMessageToNextTurn(final Activity activity) {
        if (consumed) { return; } else { consumed = true; }

        activity.setStepToNextTurn(true);
    }
  }

  public static final class ReturnFromTurnToPromiseResolution extends SteppingStrategy {
    @Override
    public boolean handleMessageReturnFromTurn() {
        if (consumed) { return false; } else { consumed = true; }
        return true;
    }
  }

  /**
   *  Return true if the given stepping strategy has been activated.
   */
  public static boolean isEnabled(final Class<? extends SteppingStrategy> strategyClass) {
    if (!VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      return false;
    }

    SteppingStrategy strategy = ActivityThread.steppingStrategy();
    if (strategy == null) {
      return false;
    }

    if (strategy instanceof ToMessageReceiver && strategyClass.equals(ToMessageReceiver.class)) {
      return strategy.handleMessageReceiver();
    } else if (strategy instanceof ToPromiseResolution && strategyClass.equals(ToPromiseResolution.class)) {
      return strategy.handleMessageToPromiseResolution();
    } else if (strategy instanceof ReturnFromTurnToPromiseResolution && strategyClass.equals(ReturnFromTurnToPromiseResolution.class)) {
      return strategy.handleMessageReturnFromTurn();
    }

    return false;
  }
}
