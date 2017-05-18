package som.vm;

import tools.debugger.entities.ActivityType;

public interface Activity {
  String getName();

  /** The activity id.
      For non-tracing activities, the id is 0. */
  default long getId() { return 0; }

  ActivityType getType();

  /** The id for the next trace buffer used with this activity.
      Since an activity is executed by at most one thread at a time,
      this id allows us to order trace buffer fragments.
      Non-tracing activities don't need to implement it. */
  default int getNextTraceBufferId() {
    throw new UnsupportedOperationException("Should never be called");
  }

  /** Set the flag that indicates a breakpoint on joining activity.
      Does nothing for non-tracing activities, i.e., when debugging is disabled. */
  default void setStepToJoin(final boolean val) { }

  void setStepToNextTurn(boolean val);
}
