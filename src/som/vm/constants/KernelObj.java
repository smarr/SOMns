package som.vm.constants;

import som.VM;
import som.vm.Symbols;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.CompilerDirectives;


public final class KernelObj {
  private KernelObj() { }
  public static final SObject kernel = new SObject(true, true);

  public static Object signalException(final String selector, final Object receiver) {
    SObjectWithClass rcvr = (SObjectWithClass) receiver;
    CompilerDirectives.transferToInterpreter();
    VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");

    // the value object was not constructed properly.
    SInvokable disp = (SInvokable) KernelObj.kernel.getSOMClass().lookupPrivate(
        Symbols.symbolFor(selector),
        KernelObj.kernel.getSOMClass().getMixinDefinition().getMixinId());
    return disp.invoke(KernelObj.kernel, rcvr.getSOMClass());
  }
}
