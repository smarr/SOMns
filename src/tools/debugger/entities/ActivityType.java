package tools.debugger.entities;

public enum ActivityType {
  PROCESS(EntityType.PROCESS, "&#10733;"),
  ACTOR(EntityType.ACTOR,     "&#128257;"),
  TASK(EntityType.TASK,       "&#8623;"),
  THREAD(EntityType.THREAD,   "&#11123;");

  private final EntityType type;
  private final String marker;

  ActivityType(final EntityType type, final String marker) {
    this.type = type;
    this.marker = marker;
  }

  public EntityType getType() { return type; }
  public String getName() { return type.name; }

  public byte getId() { return type.id; }
  public byte getCreation()   { return type.creation; }
  public byte getCompletion() { return type.completion; }
  public String getMarker() { return marker; }
}
