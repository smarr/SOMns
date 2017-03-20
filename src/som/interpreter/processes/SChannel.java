package som.interpreter.processes;

import java.util.concurrent.SynchronousQueue;

import som.primitives.processes.ChannelPrimitives;
import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import tools.concurrency.TracingChannel;
import tools.concurrency.TracingChannel.TracingChannelInput;
import tools.concurrency.TracingChannel.TracingChannelOutput;


public class SChannel extends SAbstractObject {

  public static SChannel create() {
    if (VmSettings.ACTOR_TRACING) {
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
    breakAfterRead  = false;
    breakAfterWrite = false;

    SynchronousQueue<Object> cell = new SynchronousQueue<>();

    out = SChannelOutput.create(cell, this);
    in  = SChannelInput.create(cell, this);
  }

  @Override
  public SClass getSOMClass() {
    assert ChannelPrimitives.Channel != null;
    return ChannelPrimitives.Channel;
  }

  @Override
  public boolean isValue() {
    return false;
  }

  public static class SChannelInput extends SAbstractObject {
    public static SChannelInput create(final SynchronousQueue<Object> cell,
        final SChannel channel) {
      if (VmSettings.ACTOR_TRACING) {
        return new TracingChannelInput(cell, channel);
      } else {
        return new SChannelInput(cell, channel);
      }
    }

    private final SynchronousQueue<Object> cell;
    protected final SChannel channel;

    public SChannelInput(final SynchronousQueue<Object> cell,
        final SChannel channel) {
      this.cell    = cell;
      this.channel = channel;
    }

    public Object read() throws InterruptedException {
      return cell.take();
    }

    public final Object readAndSuspendWriter(final boolean doSuspend) throws InterruptedException {
      channel.breakAfterWrite = doSuspend;
      return read();
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
  }

  public static class SChannelOutput extends SAbstractObject {
    public static SChannelOutput create(final SynchronousQueue<Object> cell,
        final SChannel channel) {
      if (VmSettings.ACTOR_TRACING) {
        return new TracingChannelOutput(cell, channel);
      } else {
        return new SChannelOutput(cell, channel);
      }
    }

    private final SynchronousQueue<Object> cell;
    protected final SChannel channel;

    protected SChannelOutput(final SynchronousQueue<Object> cell, final SChannel channel) {
      this.cell    = cell;
      this.channel = channel;
    }

    public void write(final Object value) throws InterruptedException {
      cell.put(value);
    }

    public final void writeAndSuspendReader(final Object value,
        final boolean doSuspend) throws InterruptedException {
      channel.breakAfterRead = doSuspend;
      write(value);
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
  }
}
