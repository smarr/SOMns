package tools.debugger.entities;

public enum ActivityType {
  PROCESS(EntityType.PROCESS),
  ACTOR(EntityType.ACTOR),
  TASK(EntityType.TASK),
  THREAD(EntityType.THREAD);

  private EntityType type;

  ActivityType(final EntityType type) {
    this.type = type;
  }

  public EntityType getType() { return type; }
  public String getName() { return type.name; }

  public byte getId() { return type.id; }
  public byte getCreation()   { return type.creation; }
  public byte getCompletion() { return type.completion; }
}
