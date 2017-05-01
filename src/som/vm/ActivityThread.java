package som.vm;

import tools.debugger.SteppingStrategy;

public interface ActivityThread {
  Activity getActivity();
  SteppingStrategy getSteppingStrategy();
  void setSteppingStrategy(SteppingStrategy strategy);

  static ActivityThread currentThread() { return (ActivityThread) Thread.currentThread(); }
  static SteppingStrategy steppingStrategy() { return currentThread().getSteppingStrategy(); }
}
