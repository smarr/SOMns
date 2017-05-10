package tools.concurrency;

import java.util.concurrent.SynchronousQueue;

import som.interpreter.processes.SChannel;
import tools.concurrency.ActorExecutionTrace.Events;

public final class TracingChannel extends SChannel {
  protected final long channelId;
  protected int messageId;

  public TracingChannel() {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    channelId = currentThread().generateActivityId();
    messageId = 0;
  }

  @Override
  public long getId() {
    return channelId;
  }

  private static TracingActivityThread currentThread() {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    return (TracingActivityThread) current;
  }

  public static final class TracingChannelInput extends SChannelInput {
    public TracingChannelInput(final SynchronousQueue<Object> cell,
        final SChannel channel) {
      super(cell, channel);
    }

    @Override
    public Object read() throws InterruptedException {
      TracingChannel current = (TracingChannel) channel;
      try {
        return super.read();
      } finally {
        ActorExecutionTrace.receiveOperation(Events.ChannelReceive, current.channelId);
      }
    }
  }


  public static final class TracingChannelOutput extends SChannelOutput {
    public TracingChannelOutput(final SynchronousQueue<Object> cell,
        final SChannel channel) {
      super(cell, channel);
    }

    @Override
    public void write(final Object value) throws InterruptedException {
      TracingChannel current = ((TracingChannel) channel);

      try {
        current.messageId += 1;
        super.write(value);
      } finally {
        ActorExecutionTrace.sendOperation(
            Events.ChannelSend, current.messageId, current.channelId);
      }
    }
  }
}
