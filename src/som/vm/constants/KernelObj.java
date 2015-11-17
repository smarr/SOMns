package som.vm.constants;

import som.VM;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.Symbols;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.CompilerDirectives;


public final class KernelObj {
  private KernelObj() { }
  public static final SImmutableObject kernel = new SImmutableObject(true, true);

  public static Object signalException(final String selector, final Object receiver) {
    SObjectWithClass rcvr = (SObjectWithClass) receiver;
    CompilerDirectives.transferToInterpreter();
    VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");

    // the value object was not constructed properly.
    Dispatchable disp = KernelObj.kernel.getSOMClass().lookupPrivate(
        Symbols.symbolFor(selector),
        KernelObj.kernel.getSOMClass().getMixinDefinition().getMixinId());
    return disp.invoke(KernelObj.kernel, rcvr.getSOMClass());
  }
}
