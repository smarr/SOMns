package tools.debugger.entities;


public enum SendOp {
  ACTOR_MSG(Marker.ACTOR_MSG_SEND,      EntityType.ACT_MSG, EntityType.ACTOR),
  CHANNEL_SEND(Marker.CHANNEL_MSG_SEND, EntityType.CH_MSG,  EntityType.CHANNEL),
  PROMISE_RESOLUTION(Marker.PROMISE_RESOLUTION, EntityType.PROMISE, EntityType.PROMISE);

  private final byte id;
  private final EntityType entity;
  private final EntityType target;

  SendOp(final byte id, final EntityType entity, final EntityType target) {
    this.id = id;
    this.entity = entity;
    this.target = target;
  }

  public byte getId() { return id; }
  public EntityType getEntity() { return entity; }
  public EntityType getTarget() { return target; }
  public int getSize() { return 17; }
}
