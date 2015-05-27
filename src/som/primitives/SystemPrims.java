package som.primitives;

import java.io.IOException;

import som.VM;
import som.compiler.ClassDefinition;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Bootstrap;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


public final class SystemPrims {

  @GenerateNodeFactory
  @Primitive("load:")
  public abstract static class LoadPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final String moduleName) {
      ClassDefinition module;
      try {
        module = Bootstrap.loadModule(moduleName);
        return module.instantiateClass();
      } catch (IOException e) {
        // TODO convert to SOM exception when we support them
        e.printStackTrace();
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive("exit:")
  public abstract static class ExitPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final long error) {
      VM.exit((int) error);
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive("printString:")
  public abstract static class PrintStringPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final String argument) {
      VM.print(argument);
      return argument;
    }

    @Specialization
    public final Object doSObject(final SSymbol argument) {
      return doSObject(argument.getString());
    }
  }

  @GenerateNodeFactory
  @Primitive("printNewline:")
  public abstract static class PrintInclNewlinePrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final String argument) {
      VM.println(argument);
      return argument;
    }
  }

  @GenerateNodeFactory
  @Primitive("vmArguments:")
  public abstract static class VMArgumentsPrim extends UnaryExpressionNode {
    @Specialization
    public final SArray getArguments(final Object receiver) {
      return new SArray(ArrayType.OBJECT, VM.getArguments());
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
