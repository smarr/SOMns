package tools.concurrency;

import java.util.concurrent.SynchronousQueue;

import som.interpreter.processes.SChannel;
import tools.debugger.entities.ReceiveOp;
import tools.debugger.entities.SendOp;


public final class TracingChannel extends SChannel {
  protected final long channelId;
  protected int        messageId;

  public TracingChannel() {
    channelId = TracingActivityThread.newEntityId();
    messageId = 0;
  }

  @Override
  public long getId() {
    return channelId;
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
        ActorExecutionTrace.receiveOperation(ReceiveOp.CHANNEL_RCV, current.channelId);
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
            SendOp.CHANNEL_SEND, current.messageId, current.channelId);
      }
    }
  }
}
