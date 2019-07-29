package tools.concurrency;

import java.util.concurrent.SynchronousQueue;

import som.interpreter.processes.SChannel;
import tools.debugger.entities.ReceiveOp;
import tools.debugger.entities.SendOp;
import tools.replay.nodes.RecordEventNodes.RecordTwoEvent;


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
    public Object read(final RecordTwoEvent traceRead) throws InterruptedException {
      TracingChannel current = (TracingChannel) channel;
      try {
        return super.read(traceRead);
      } finally {
        KomposTrace.receiveOperation(ReceiveOp.CHANNEL_RCV, current.channelId);
      }
    }
  }

  public static final class TracingChannelOutput extends SChannelOutput {
    public TracingChannelOutput(final SynchronousQueue<Object> cell,
        final SChannel channel) {
      super(cell, channel);
    }

    @Override
    public void write(final Object value, final RecordTwoEvent traceWrite)
        throws InterruptedException {
      TracingChannel current = ((TracingChannel) channel);

      try {
        current.messageId += 1;
        super.write(value, traceWrite);
      } finally {
        KomposTrace.sendOperation(
            SendOp.CHANNEL_SEND, current.messageId, current.channelId);
      }
    }
  }
}
