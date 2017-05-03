package som.vm;

import tools.debugger.entities.ActivityType;

public interface Activity {
  String getName();
  long getId();
  ActivityType getType();

  void setStepToJoin(boolean val);
}
