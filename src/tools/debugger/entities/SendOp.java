package tools.debugger.entities;

import tools.TraceData;

public enum SendOp {
  ACTOR_MSG(Marker.ACTOR_MSG_SEND, EntityType.ACT_MSG, EntityType.ACTOR),
  PROMISE_MSG(Marker.PROMISE_MSG_SEND, EntityType.ACT_MSG, EntityType.PROMISE),
  CHANNEL_SEND(Marker.CHANNEL_MSG_SEND, EntityType.CH_MSG, EntityType.CHANNEL),
  PROMISE_RESOLUTION(Marker.PROMISE_RESOLUTION, EntityType.PROMISE, EntityType.PROMISE);

  private final byte       id;
  private final EntityType entity;
  private final EntityType target;

  private static final int SYMBOL_ID_SIZE = 2;
  private static final int RECEIVER_ACTOR_ID_SIZE = 8;
  private static final int PROMISE_VALUE_SIZE = 4;

  SendOp(final byte id, final EntityType entity, final EntityType target) {
    this.id = id;
    this.entity = entity;
    this.target = target;
  }

  public byte getId() {
    return id;
  }

  public EntityType getEntity() {
    return entity;
  }

  public EntityType getTarget() {
    return target;
  }

  public int getSize() {
    return 17 + RECEIVER_ACTOR_ID_SIZE + SYMBOL_ID_SIZE + TraceData.SOURCE_SECTION_SIZE;
  }

  //Adds the promise value size and the number of bytes for an integer (4), which is also recorded
  public int getSize(byte[] promiseValue) {
    return 17 + RECEIVER_ACTOR_ID_SIZE + SYMBOL_ID_SIZE + TraceData.SOURCE_SECTION_SIZE + promiseValue.length + PROMISE_VALUE_SIZE;
  }
}
