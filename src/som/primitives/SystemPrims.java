package som.primitives;

import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public class SystemPrims {
  public abstract static class LoadPrim extends BinaryMessageNode {
    public LoadPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public LoadPrim(final LoadPrim prim) { this(prim.selector, prim.universe); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SSymbol argument) {
      SClass result = universe.loadClass(argument);
      return result != null ? result : universe.nilObject;
    }
  }

  public abstract static class ExitPrim extends BinaryMessageNode {
    public ExitPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public ExitPrim(final ExitPrim prim) { this(prim.selector, prim.universe); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final int error) {
      universe.exit(error);
      return receiver;
    }
  }

  public abstract static class GlobalPrim extends BinaryMessageNode {
    public GlobalPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public GlobalPrim(final GlobalPrim prim) { this(prim.selector, prim.universe); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SSymbol argument) {
      SAbstractObject result = universe.getGlobal(argument);
      return result != null ? result : universe.nilObject;
    }
  }

  public abstract static class GlobalPutPrim extends TernaryMessageNode {
    public GlobalPutPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public GlobalPutPrim(final GlobalPutPrim prim) { this(prim.selector, prim.universe); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SSymbol global,
        final SAbstractObject value) {
      universe.setGlobal(global, value);
      return value;
    }
  }

  public abstract static class PrintStringPrim extends BinaryMessageNode {
    public PrintStringPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public PrintStringPrim(final PrintStringPrim prim) { this(prim.selector, prim.universe); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final String argument) {
      Universe.print(argument);
      return receiver;
    }
  }

  public abstract static class PrintNewlinePrim extends UnaryMessageNode {
    public PrintNewlinePrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public PrintNewlinePrim(final PrintNewlinePrim prim) { this(prim.selector, prim.universe); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver) {
      Universe.println();
      return receiver;
    }
  }

  public abstract static class FullGCPrim extends UnaryMessageNode {
    public FullGCPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public FullGCPrim(final FullGCPrim prim) { this(prim.selector, prim.universe); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver) {
      System.gc();
      return universe.trueObject;
    }
  }

  public abstract static class TimePrim extends UnaryMessageNode {
    public TimePrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public TimePrim(final TimePrim prim) { this(prim.selector, prim.universe); }

    @Specialization(guards = "receiverIsSystemObject")
    public int doSObject(final SObject receiver) {
      return (int) (System.currentTimeMillis() - startTime);
    }
  }

  public abstract static class TicksPrim extends UnaryMessageNode {
    public TicksPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public TicksPrim(final TicksPrim prim) { this(prim.selector, prim.universe); }

    @Specialization(guards = "receiverIsSystemObject")
    public int doSObject(final SObject receiver) {
      return (int) (System.nanoTime() / 1000L - startMicroTime);
    }
  }

  {
    startMicroTime = System.nanoTime() / 1000L;
    startTime = startMicroTime / 1000L;
  }
  private static long startTime;
  private static long startMicroTime;
}
