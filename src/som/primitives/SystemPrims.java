package som.primitives;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Universe;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


public final class SystemPrims {

  @GenerateNodeFactory
  public abstract static class BinarySystemNode extends BinaryExpressionNode {
    protected final Universe universe;
    protected BinarySystemNode() { super(null); this.universe = Universe.current(); }
  }

  public abstract static class LoadPrim extends BinarySystemNode {
    @Specialization
    public final Object doSObject(final SObject receiver, final SSymbol argument) {
      SClass result = universe.loadClass(argument);
      return result != null ? result : Nil.nilObject;
    }
  }

  public abstract static class ExitPrim extends BinarySystemNode {
    @Specialization
    public final Object doSObject(final SObject receiver, final long error) {
      universe.exit((int) error);
      return receiver;
    }
  }

  public abstract static class PrintStringPrim extends BinarySystemNode {
    @Specialization
    public final Object doSObject(final SObject receiver, final String argument) {
      Universe.print(argument);
      return receiver;
    }

    @Specialization
    public final Object doSObject(final SObject receiver, final SSymbol argument) {
      return doSObject(receiver, argument.getString());
    }
  }

  @GenerateNodeFactory
  public abstract static class PrintNewlinePrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final SObject receiver) {
      Universe.println();
      return receiver;
    }
  }

  @GenerateNodeFactory
  public abstract static class PrintInclNewlinePrim extends BinaryExpressionNode {
    @Specialization
    public final Object doSObject(final SObject receiver, final String argument) {
      Universe.println(argument);
      return receiver;
    }
  }

  @GenerateNodeFactory
  public abstract static class FullGCPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final SObject receiver) {
      System.gc();
      return true;
    }
  }

  @GenerateNodeFactory
  public abstract static class TimePrim extends UnaryExpressionNode {
    @Specialization
    public final long doSObject(final SObject receiver) {
      return System.currentTimeMillis() - startTime;
    }
  }

  @GenerateNodeFactory
  public abstract static class TicksPrim extends UnaryExpressionNode {
    @Specialization
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
