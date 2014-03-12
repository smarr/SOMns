package som.primitives;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class SystemPrims {
  private abstract static class BinarySystemNode extends BinaryExpressionNode {
    protected final Universe universe;
    protected BinarySystemNode() { this.universe = Universe.current(); }

    protected boolean receiverIsSystemObject(final SAbstractObject receiver) {
      return receiver == universe.systemObject;
    }
  }

  private abstract static class UnarySystemNode extends UnaryExpressionNode {
    protected final Universe universe;
    protected UnarySystemNode() { this.universe = Universe.current(); }

    protected boolean receiverIsSystemObject(final SAbstractObject receiver) {
      return receiver == universe.systemObject;
    }
  }

  public abstract static class LoadPrim extends BinarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SSymbol argument) {
      SClass result = universe.loadClass(argument);
      return result != null ? result : universe.nilObject;
    }
  }

  public abstract static class ExitPrim extends BinarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final int error) {
      universe.exit(error);
      return receiver;
    }
  }

  public abstract static class GlobalPrim extends BinarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SSymbol argument) {
      SAbstractObject result = universe.getGlobal(argument);
      return result != null ? result : universe.nilObject;
    }
    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  public abstract static class GlobalPutPrim extends TernaryExpressionNode {
    private final Universe universe;
    public GlobalPutPrim()  { this.universe = Universe.current(); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SSymbol global,
        final SAbstractObject value) {
      universe.setGlobal(global, value);
      return value;
    }

    protected boolean receiverIsSystemObject(final SAbstractObject receiver) {
      return receiver == universe.systemObject;
    }
  }

  public abstract static class PrintStringPrim extends BinarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final String argument) {
      Universe.print(argument);
      return receiver;
    }
  }

  public abstract static class PrintNewlinePrim extends UnarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver) {
      Universe.println();
      return receiver;
    }
  }

  public abstract static class FullGCPrim extends UnarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver) {
      // TODO: deactivated GC as work around for Hotspot collecting native code
      //       generated for the benchmarks.
      // System.gc();
      // return universe.trueObject;
      return universe.falseObject;
    }
  }

  public abstract static class TimePrim extends UnarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public int doSObject(final SObject receiver) {
      return (int) (System.currentTimeMillis() - startTime);
    }
    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  public abstract static class TicksPrim extends UnarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public int doSObject(final SObject receiver) {
      return (int) (System.nanoTime() / 1000L - startMicroTime);
    }
    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  {
    startMicroTime = System.nanoTime() / 1000L;
    startTime = startMicroTime / 1000L;
  }
  private static long startTime;
  private static long startMicroTime;
}
