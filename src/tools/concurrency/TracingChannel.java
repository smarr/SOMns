package tools.concurrency;

import java.util.concurrent.SynchronousQueue;

import som.interpreter.processes.SChannel;

public final class TracingChannel extends SChannel {
  protected final long channelId;
  protected volatile long lastReceiverId;

  public TracingChannel() {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    channelId = currentThread().generateActivityId();
  }

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
      ((TracingChannel) channel).lastReceiverId = currentThread().getActivity().getId();
      return super.read();
    }
  }


  public static final class TracingChannelOutput extends SChannelOutput {
    public TracingChannelOutput(final SynchronousQueue<Object> cell, final SChannel channel) {
      super(cell, channel);
    }

    @Override
    public void write(final Object value) throws InterruptedException {
      super.write(value);
      TracingChannel current = ((TracingChannel) channel);
      final long lastRcvr = current.lastReceiverId;
      final long sender   = currentThread().getActivity().getId();
      ActorExecutionTrace.channelMessage(current.channelId, sender, lastRcvr, value);
    }
  }
}
