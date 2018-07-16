package tools.replay;

public class StringWrapper {
  public final String s;
  public final int    actorId;
  public final int    dataId;

  public StringWrapper(final String s, final int actorId, final int dataId) {
    super();
    this.s = s;
    this.actorId = actorId;
    this.dataId = dataId;
  }
}
