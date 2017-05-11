package som.vm;

import tools.debugger.entities.SteppingType;


// TODO: think this is not needed anymore and can be removed
public interface ActivityThread {
  Activity getActivity();
  boolean isStepping(SteppingType type);
  void setSteppingStrategy(SteppingType type);

  static ActivityThread currentThread() { return (ActivityThread) Thread.currentThread(); }
  static boolean isSteppingType(final SteppingType type) { return currentThread().isStepping(type); }
}
