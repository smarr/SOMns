package som.interpreter.processes;

import java.util.concurrent.SynchronousQueue;

import som.primitives.processes.ChannelPrimitives;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;


public class SChannel extends SAbstractObject {

  public final SChannelOutput out;
  public final SChannelInput  in;

  public SChannel() {
    SynchronousQueue<Object> cell = new SynchronousQueue<>();
    out = new SChannelOutput(cell);
    in  = new SChannelInput(cell);
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

  public static final class SChannelInput extends SAbstractObject {
    private final SynchronousQueue<Object> cell;

    public SChannelInput(final SynchronousQueue<Object> cell) {
      this.cell = cell;
    }

    public Object read() throws InterruptedException {
      return cell.take();
    }

    @Override
    public SClass getSOMClass() {
      assert ChannelPrimitives.In != null;
      return ChannelPrimitives.In;
    }

    @Override
    public boolean isValue() {
      return true;
    }
  }

  public static final class SChannelOutput extends SAbstractObject {
    private final SynchronousQueue<Object> cell;

    public SChannelOutput(final SynchronousQueue<Object> cell) {
      this.cell = cell;
    }

    public void write(final Object value) throws InterruptedException {
      cell.put(value);
    }

    @Override
    public SClass getSOMClass() {
      assert ChannelPrimitives.Out != null;
      return ChannelPrimitives.Out;
    }

    @Override
    public boolean isValue() {
      return true;
    }
  }
}
