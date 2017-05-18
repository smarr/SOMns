package tools.debugger.entities;


public enum Implementation {
  IMPL_THREAD(Marker.IMPL_THREAD, 9),
  IMPL_CURRENT_ACTIVITY(Marker.IMPL_THREAD_CURRENT_ACTIVITY, 13);

  private final byte id;
  private final int  size;

  Implementation(final byte id, final int size) {
    this.id = id;
    this.size = size;
  }

  public byte getId()  { return id; }
  public int getSize() { return size; }
}
