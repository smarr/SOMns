package tools.debugger.entities;

public enum ReceiveOp {
  CHANNEL_RCV(Marker.CHANNEL_MSG_RCV, EntityType.CHANNEL),
  TASK_JOIN(Marker.TASK_JOIN, EntityType.TASK),
  THREAD_JOIN(Marker.THREAD_JOIN, EntityType.THREAD);

  private final byte       id;
  private final EntityType source;

  ReceiveOp(final byte id, final EntityType source) {
    this.id = id;
    this.source = source;
  }

  public byte getId() {
    return id;
  }

  public EntityType getSource() {
    return source;
  }

  public int getSize() {
    return 9;
  }
}
