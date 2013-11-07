package som.primitives;

import som.interpreter.nodes.messages.BinaryMonomorphicNode;
import som.interpreter.nodes.messages.TernaryMonomorphicNode;
import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public class SystemPrims {
  public abstract static class LoadPrim extends BinaryMonomorphicNode {
    public LoadPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public LoadPrim(final LoadPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SSymbol argument) {
      SClass result = universe.loadClass(argument);
      return result != null ? result : universe.nilObject;
    }
  }

  public abstract static class ExitPrim extends BinaryMonomorphicNode {
    public ExitPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public ExitPrim(final ExitPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SInteger error) {
      throw new RuntimeException("Make sure system primitives do not trigger on any other kind of object");
      // TODO:
//      universe.exit(error.getEmbeddedInteger());
//      return receiver;
    }
  }

  public abstract static class GlobalPrim extends BinaryMonomorphicNode {
    public GlobalPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public GlobalPrim(final GlobalPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SSymbol argument) {
      SAbstractObject result = universe.getGlobal(argument);
      return result != null ? result : universe.nilObject;
    }
  }

  public abstract static class GlobalPutPrim extends TernaryMonomorphicNode {
    public GlobalPutPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public GlobalPutPrim(final GlobalPutPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SSymbol global,
        final SAbstractObject value) {
      universe.setGlobal(global, value);
      return value;
    }
  }

  public abstract static class PrintStringPrim extends BinaryMonomorphicNode {
    public PrintStringPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public PrintStringPrim(final PrintStringPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver, final SString argument) {
      Universe.print(argument.getEmbeddedString());
      return receiver;
    }
  }

  public abstract static class PrintNewlinePrim extends UnaryMonomorphicNode {
    public PrintNewlinePrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public PrintNewlinePrim(final PrintNewlinePrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver) {
      Universe.println();
      return receiver;
    }
  }

  public abstract static class FullGCPrim extends UnaryMonomorphicNode {
    public FullGCPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public FullGCPrim(final FullGCPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "receiverIsSystemObject")
    public Object doSObject(final SObject receiver) {
      System.gc();
      return universe.trueObject;
    }
  }

  public abstract static class TimePrim extends UnaryMonomorphicNode {
    public TimePrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public TimePrim(final TimePrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "receiverIsSystemObject")
    public SAbstractObject doSObject(final SObject receiver) {
      int time = (int) (System.currentTimeMillis() - startTime);
      return universe.newInteger(time);
    }
  }

  public abstract static class TicksPrim extends UnaryMonomorphicNode {
    public TicksPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public TicksPrim(final TicksPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization(guards = "receiverIsSystemObject")
    public SAbstractObject doSObject(final SObject receiver) {
      int time = (int) (System.nanoTime() / 1000L - startMicroTime);
      return universe.newInteger(time);
    }
  }

  {
    startMicroTime = System.nanoTime() / 1000L;
    startTime = startMicroTime / 1000L;
  }
  private static long startTime;
  private static long startMicroTime;
}
