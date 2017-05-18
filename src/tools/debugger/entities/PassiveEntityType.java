package tools.debugger.entities;

import tools.TraceData;


public enum PassiveEntityType {
  CHANNEL(EntityType.CHANNEL, Marker.CHANNEL_CREATION),
  CHANNEL_MSG(EntityType.CH_MSG),
  ACTOR_MSG(EntityType.ACT_MSG),
  PROMISE(EntityType.PROMISE, Marker.PROMISE_CREATION);

  private final EntityType type;
  private final byte creationMarker;

  PassiveEntityType(final EntityType type) {
    this(type, 0);
  }

  PassiveEntityType(final EntityType type, final int creationMarker) {
    this.type = type;
    this.creationMarker = (byte) creationMarker;
  }

  public byte getId()     { return type.id; }
  public String getName() { return type.name; }

  public byte getCreationMarker() { return creationMarker; }
  public int getCreationSize() { return 9 + TraceData.SOURCE_SECTION_SIZE; }
}
