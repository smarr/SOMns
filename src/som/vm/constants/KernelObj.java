package som.vm.constants;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.VM;
import som.interpreter.Types;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Symbols;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;


public final class KernelObj {
  private KernelObj() {}

  public static final SImmutableObject   kernel = new SImmutableObject(true, true);
  @CompilationFinal public static SClass indexOutOfBoundsClass;

  public static Object signalException(final String selector, final Object arg) {
    CompilerDirectives.transferToInterpreter();
    VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");

    SInvokable disp = (SInvokable) KernelObj.kernel.getSOMClass().lookupPrivate(
        Symbols.symbolFor(selector),
        KernelObj.kernel.getSOMClass().getMixinDefinition().getMixinId());

    return disp.invoke(new Object[] {KernelObj.kernel, arg});
  }

  public static Object signalExceptionWithClass(final String selector, final Object receiver) {
    CompilerDirectives.transferToInterpreter();
    SClass clazz = Types.getClassOf(receiver);
    return signalException(selector, clazz);
  }

  @GenerateNodeFactory
  @Primitive(primitive = "kernelIndexOutOfBounds:")
  public abstract static class SetIndexOutOfBounds extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      assert indexOutOfBoundsClass == null;
      indexOutOfBoundsClass = value;
      return value;
    }
  }
}
