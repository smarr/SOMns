package som.interpreter.processes;

import java.util.concurrent.SynchronousQueue;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.processes.ChannelPrimitives;
import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import tools.concurrency.TracingChannel;
import tools.concurrency.TracingChannel.TracingChannelInput;
import tools.concurrency.TracingChannel.TracingChannelOutput;
import tools.replay.PassiveEntityWithEvents;
import tools.replay.ReplayData;
import tools.replay.nodes.RecordEventNodes.RecordTwoEvent;


public class SChannel extends SAbstractObject {

  public static SChannel create() {
    if (VmSettings.ACTOR_TRACING || VmSettings.KOMPOS_TRACING || VmSettings.REPLAY) {
      return new TracingChannel();
    } else {
      return new SChannel();
    }
  }

  public final SChannelOutput out;
  public final SChannelInput  in;

  /** Indicate that a breakpoint on the writer requested a suspension on read. */
  private volatile boolean breakAfterRead;

  /** Indicate that a breakpoint on the reader requested a suspension on write. */
  private volatile boolean breakAfterWrite;

  protected SChannel() {
    breakAfterRead = false;
    breakAfterWrite = false;

    SynchronousQueue<Object> cell = new SynchronousQueue<>();

    out = SChannelOutput.create(cell, this);
    in = SChannelInput.create(cell, this);
  }

  @Override
  public SClass getSOMClass() {
    assert ChannelPrimitives.Channel != null;
    return ChannelPrimitives.Channel;
  }

  public long getId() {
    throw new UnsupportedOperationException("Should never be called.");
  }

  @Override
  public boolean isValue() {
    return false;
  }

  public static class SChannelInput extends SAbstractObject
      implements PassiveEntityWithEvents {
    public static SChannelInput create(final SynchronousQueue<Object> cell,
        final SChannel channel) {
      if (VmSettings.KOMPOS_TRACING) {
        return new TracingChannelInput(cell, channel);
      } else {
        return new SChannelInput(cell, channel);
      }
    }

    private final SynchronousQueue<Object> cell;
    protected final SChannel               channel;
    private int                            numReads = 0;

    public SChannelInput(final SynchronousQueue<Object> cell,
        final SChannel channel) {
      this.cell = cell;
      this.channel = channel;
    }

    @TruffleBoundary
    public Object read(final RecordTwoEvent traceRead) throws InterruptedException {
      ObjectTransitionSafepoint.INSTANCE.unregister();

      if (VmSettings.REPLAY) {
        ReplayData.replayDelayNumberedEvent(this, channel.getId());
      }

      try {
        synchronized (this) {
          if (VmSettings.ACTOR_TRACING) {
            traceRead.record(channel.getId(), numReads);
            numReads++;
          }
          return cell.take();
        }
      } finally {
        if (VmSettings.REPLAY) {
          numReads++;
        }
        ObjectTransitionSafepoint.INSTANCE.register();
      }
    }

    public final Object readAndSuspendWriter(final boolean doSuspend,
        final RecordTwoEvent traceRead)
        throws InterruptedException {
      channel.breakAfterWrite = doSuspend;
      return read(traceRead);
    }

    public final boolean shouldBreakAfterRead() {
      return channel.breakAfterRead;
    }

    @Override
    public final SClass getSOMClass() {
      assert ChannelPrimitives.In != null;
      return ChannelPrimitives.In;
    }

    @Override
    public final boolean isValue() {
      return true;
    }

    @Override
    public int getNextEventNumber() {
      synchronized (this) {
        return numReads;
      }
    }
  }

  public static class SChannelOutput extends SAbstractObject
      implements PassiveEntityWithEvents {
    public static SChannelOutput create(final SynchronousQueue<Object> cell,
        final SChannel channel) {
      if (VmSettings.KOMPOS_TRACING) {
        return new TracingChannelOutput(cell, channel);
      } else {
        return new SChannelOutput(cell, channel);
      }
    }

    private final SynchronousQueue<Object> cell;
    protected final SChannel               channel;
    private int                            numWrites = 0;

    protected SChannelOutput(final SynchronousQueue<Object> cell, final SChannel channel) {
      this.cell = cell;
      this.channel = channel;
    }

    @TruffleBoundary
    public void write(final Object value, final RecordTwoEvent traceWrite)
        throws InterruptedException {
      ObjectTransitionSafepoint.INSTANCE.unregister();

      if (VmSettings.REPLAY) {
        ReplayData.replayDelayNumberedEvent(this, channel.getId());
      }

      try {
        synchronized (this) {
          if (VmSettings.ACTOR_TRACING) {
            traceWrite.record(channel.getId(), numWrites);
            numWrites++;
          }
          cell.put(value);
        }

      } finally {
        if (VmSettings.REPLAY) {
          synchronized (this) {
            numWrites++;
          }
        }
        ObjectTransitionSafepoint.INSTANCE.register();
      }
    }

    public final void writeAndSuspendReader(final Object value,
        final boolean doSuspend, final RecordTwoEvent traceWrite) throws InterruptedException {
      channel.breakAfterRead = doSuspend;
      write(value, traceWrite);
    }

    public final boolean shouldBreakAfterWrite() {
      return channel.breakAfterWrite;
    }

    @Override
    public final SClass getSOMClass() {
      assert ChannelPrimitives.Out != null;
      return ChannelPrimitives.Out;
    }

    @Override
    public final boolean isValue() {
      return true;
    }

    @Override
    public int getNextEventNumber() {
      synchronized (this) {
        return numWrites;
      }
    }
  }
}
