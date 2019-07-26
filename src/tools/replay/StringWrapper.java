package tools.replay;

public class StringWrapper {
  public final String s;
  public final long   actorId;
  public final int    dataId;

  public StringWrapper(final String s, final long l, final int dataId) {
    super();
    this.s = s;
    this.actorId = l;
    this.dataId = dataId;
  }
}
