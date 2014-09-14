package som.primitives;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Universe;
import som.vm.constants.Globals;
import som.vm.constants.Nil;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class SystemPrims {
  public abstract static class BinarySystemNode extends BinaryExpressionNode {
    protected final Universe universe;
    protected BinarySystemNode() { super(null); this.universe = Universe.current(); }

    protected final boolean receiverIsSystemObject(final SAbstractObject receiver) {
      return receiver == Globals.systemObject;
    }
  }

  private abstract static class UnarySystemNode extends UnaryExpressionNode {
    protected UnarySystemNode() { super(null); }

    protected final boolean receiverIsSystemObject(final SAbstractObject receiver) {
      return receiver == Globals.systemObject;
    }
  }

  public abstract static class LoadPrim extends BinarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public final Object doSObject(final SObject receiver, final SSymbol argument) {
      SClass result = universe.loadClass(argument);
      return result != null ? result : Nil.nilObject;
    }
  }

  public abstract static class ExitPrim extends BinarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public final Object doSObject(final SObject receiver, final long error) {
      universe.exit((int) error);
      return receiver;
    }
  }

  public abstract static class GlobalPutPrim extends TernaryExpressionNode {
    private final Universe universe;
    public GlobalPutPrim()  { this.universe = Universe.current(); }

    @Specialization(guards = "receiverIsSystemObject")
    public final Object doSObject(final SObject receiver, final SSymbol global,
        final Object value) {
      universe.setGlobal(global, value);
      return value;
    }

    protected final boolean receiverIsSystemObject(final SAbstractObject receiver) {
      return receiver == Globals.systemObject;
    }
  }

  public abstract static class PrintStringPrim extends BinarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public final Object doSObject(final SObject receiver, final String argument) {
      Universe.print(argument);
      return receiver;
    }
  }

  public abstract static class PrintNewlinePrim extends UnarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public final Object doSObject(final SObject receiver) {
      Universe.println();
      return receiver;
    }
  }

  public abstract static class FullGCPrim extends UnarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public final Object doSObject(final SObject receiver) {
      System.gc();
      return true;
    }
  }

  public abstract static class TimePrim extends UnarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public final long doSObject(final SObject receiver) {
      return System.currentTimeMillis() - startTime;
    }
    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  public abstract static class TicksPrim extends UnarySystemNode {
    @Specialization(guards = "receiverIsSystemObject")
    public final long doSObject(final SObject receiver) {
      return System.nanoTime() / 1000L - startMicroTime;
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
