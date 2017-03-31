package som.vm;

import tools.debugger.entities.ActivityType;

public interface Activity {
  String getName();

  /** The activity id.
      For non-tracing activities, the id is 0. */
  default long getId() { return 0; }

  ActivityType getType();

  /** Set the flag that indicates a breakpoint on joining activity.
      Does nothing for non-tracing activities, i.e., when debugging is disabled. */
  default void setStepToJoin(final boolean val) { }

  void setStepToNextTurn(boolean val);
}
