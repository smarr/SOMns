package som.vm.constants;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vm.Symbols;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObjectWithClass;


public final class KernelObj {
  private KernelObj() { }
  public static final SImmutableObject kernel = new SImmutableObject(true, true);
  @CompilationFinal public static SClass indexOutOfBoundsClass;

  public static Object signalException(final String selector, final Object receiver) {
    SObjectWithClass rcvr = (SObjectWithClass) receiver;
    CompilerDirectives.transferToInterpreter();
    VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");

    // the value object was not constructed properly.
    SInvokable disp = (SInvokable) KernelObj.kernel.getSOMClass().lookupPrivate(
        Symbols.symbolFor(selector),
        KernelObj.kernel.getSOMClass().getMixinDefinition().getMixinId());
    return disp.invoke(new Object[] {KernelObj.kernel, rcvr.getSOMClass()});
  }

  @GenerateNodeFactory
  @Primitive(primitive = "kernelIndexOutOfBounds:")
  public abstract static class SetIndexOutOfBounds extends UnaryExpressionNode {
    public SetIndexOutOfBounds(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final SClass setClass(final SClass value) {
      assert indexOutOfBoundsClass == null;
      indexOutOfBoundsClass = value;
      return value;
    }
  }
}
