package tools.replay;

public class ReplayRecord {
  public final long        eventNo;
  public final TraceRecord type;

  public ReplayRecord(final long eventNo,
      final TraceRecord type) {
    this.eventNo = eventNo;
    this.type = type;
  }

  public boolean getBoolean() {
    return this.eventNo == 1;
  }
}
