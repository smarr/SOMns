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

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;


public final class SystemPrims {
  public static boolean receiverIsSystemObject(final SAbstractObject receiver) {
    return receiver == Globals.systemObject;
  }

  @GenerateNodeFactory
  public abstract static class BinarySystemNode extends BinaryExpressionNode {
    protected final Universe universe;
    protected BinarySystemNode() { super(null); this.universe = Universe.current(); }
  }

  @ImportStatic(SystemPrims.class)
  public abstract static class LoadPrim extends BinarySystemNode {
    @Specialization(guards = "receiverIsSystemObject(receiver)")
    public final Object doSObject(final SObject receiver, final SSymbol argument) {
      SClass result = universe.loadClass(argument);
      return result != null ? result : Nil.nilObject;
    }
  }

  @ImportStatic(SystemPrims.class)
  public abstract static class ExitPrim extends BinarySystemNode {
    @Specialization(guards = "receiverIsSystemObject(receiver)")
    public final Object doSObject(final SObject receiver, final long error) {
      universe.exit((int) error);
      return receiver;
    }
  }

  @ImportStatic(SystemPrims.class)
  @GenerateNodeFactory
  public abstract static class GlobalPutPrim extends TernaryExpressionNode {
    private final Universe universe;
    public GlobalPutPrim()  { this.universe = Universe.current(); }

    @Specialization(guards = "receiverIsSystemObject(receiver)")
    public final Object doSObject(final SObject receiver, final SSymbol global,
        final Object value) {
      universe.setGlobal(global, value);
      return value;
    }
  }

  @ImportStatic(SystemPrims.class)
  public abstract static class PrintStringPrim extends BinarySystemNode {
    @Specialization(guards = "receiverIsSystemObject(receiver)")
    public final Object doSObject(final SObject receiver, final String argument) {
      Universe.print(argument);
      return receiver;
    }

    @Specialization(guards = "receiverIsSystemObject(receiver)")
    public final Object doSObject(final SObject receiver, final SSymbol argument) {
      return doSObject(receiver, argument.getString());
    }
  }

  @ImportStatic(SystemPrims.class)
  @GenerateNodeFactory
  public abstract static class PrintNewlinePrim extends UnaryExpressionNode {
    @Specialization(guards = "receiverIsSystemObject(receiver)")
    public final Object doSObject(final SObject receiver) {
      Universe.println();
      return receiver;
    }
  }

  @ImportStatic(SystemPrims.class)
  @GenerateNodeFactory
  public abstract static class PrintInclNewlinePrim extends BinaryExpressionNode {
    @Specialization(guards = "receiverIsSystemObject(receiver)")
    public final Object doSObject(final SObject receiver, final String argument) {
      Universe.println(argument);
      return receiver;
    }
  }

  @ImportStatic(SystemPrims.class)
  @GenerateNodeFactory
  public abstract static class FullGCPrim extends UnaryExpressionNode {
    @Specialization(guards = "receiverIsSystemObject(receiver)")
    public final Object doSObject(final SObject receiver) {
      System.gc();
      return true;
    }
  }

  @ImportStatic(SystemPrims.class)
  @GenerateNodeFactory
  public abstract static class TimePrim extends UnaryExpressionNode {
    @Specialization(guards = "receiverIsSystemObject(receiver)")
    public final long doSObject(final SObject receiver) {
      return System.currentTimeMillis() - startTime;
    }
  }

  @ImportStatic(SystemPrims.class)
  @GenerateNodeFactory
  public abstract static class TicksPrim extends UnaryExpressionNode {
    @Specialization(guards = "receiverIsSystemObject(receiver)")
    public final long doSObject(final SObject receiver) {
      return System.nanoTime() / 1000L - startMicroTime;
    }
  }

  {
    startMicroTime = System.nanoTime() / 1000L;
    startTime = startMicroTime / 1000L;
  }
  private static long startTime;
  private static long startMicroTime;
}
