package som.interpreter.actors;

import java.util.ArrayList;
import java.util.List;

import som.VmSettings;
import tools.ObjectBuffer;

public class Mailbox extends ObjectBuffer<EventualMessage>{
  public Mailbox(final int bufferSize) {
    super(bufferSize);
  }

  public void setExecutionStart(final long start) { }
  public void setBaseMessageId(final long id) { }
  public void addMessageSendTime() { }
  public void addMessageExecutionStart() { }

  public long getExecutionStart() { return 0; }
  public long getBasemessageId() { return 0; }
  public long getMessageSendTime(final int idx) { return 0; }
  public long getMessageExecutionStart(final int idx) { return 0; }

  public static Mailbox createNewMailbox(final int bufferSize) {
    if (VmSettings.ACTOR_TRACING) {
      return new TracingMailbox(bufferSize);
    } else {
      return new Mailbox(bufferSize);
    }
  }

  public static final class TracingMailbox extends Mailbox {
    // TODO the timestamps are very expensive, hide behind feature flag.
    long baseMessageId;
    long executionStart;
    final List<Long> messageExecutionStart = new ArrayList<>();
    final List<Long> messageSendTime = new ArrayList<>();

    public TracingMailbox(final int bufferSize) {
      super(bufferSize);
    }

    @Override
    public void setExecutionStart(final long start) {
      this.executionStart = start;
    }

    @Override
    public void setBaseMessageId(final long id) {
      this.baseMessageId = id;
    }

    @Override
    public long getExecutionStart() {
      return executionStart;
    }

    @Override
    public long getBasemessageId() {
      return baseMessageId;
    }

    @Override
    public void addMessageSendTime() {
      messageSendTime.add(System.nanoTime());
    }

    @Override
    public void addMessageExecutionStart() {
      messageExecutionStart.add(System.nanoTime());
    }

    @Override
    public long getMessageSendTime(final int idx) {
      return messageSendTime.get(idx);
    }

    @Override
    public long getMessageExecutionStart(final int idx) {
      return messageExecutionStart.get(idx);
    }
  }
}
