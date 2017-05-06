package som.vm;

import tools.debugger.SteppingStrategy;


// TODO: think this is not needed anymore and can be removed
public interface ActivityThread {
  Activity getActivity();
  SteppingStrategy getSteppingStrategy();
  void setSteppingStrategy(SteppingStrategy strategy);

  static ActivityThread currentThread() { return (ActivityThread) Thread.currentThread(); }
  static SteppingStrategy steppingStrategy() { return currentThread().getSteppingStrategy(); }
}
