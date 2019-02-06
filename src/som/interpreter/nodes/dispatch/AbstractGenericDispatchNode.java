package som.interpreter.nodes.dispatch;

import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.Output;
import som.VM;
import som.interpreter.Invokable;
import som.interpreter.SArguments;
import som.interpreter.SomLanguage;
import som.interpreter.Types;
import som.primitives.SystemPrims.PrintStackTracePrim;
import som.vm.NotYetImplementedException;
import som.vm.VmSettings;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;
import tools.asyncstacktraces.ShadowStackEntryLoad;


public abstract class AbstractGenericDispatchNode extends AbstractDispatchNode {
  @Child protected IndirectCallNode     call;
  protected final SSymbol               selector;
  @Child protected ShadowStackEntryLoad shadowStackEntryLoad = ShadowStackEntryLoad.create();

  public AbstractGenericDispatchNode(final SourceSection source,
      final SSymbol selector) {
    super(source);
    this.selector = selector;
    call = Truffle.getRuntime().createIndirectCallNode();
  }

  protected abstract Dispatchable doLookup(SClass rcvrClass);

  @Override
  public final Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    Object rcvr = arguments[0];
    SClass rcvrClass = Types.getClassOf(rcvr);
    Dispatchable method = doLookup(rcvrClass);

    // Here we fall back to the slow case since megamorphic sends
    // are just not present in benchmarks
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      SArguments.setShadowStackEntryWithCache(arguments, this,
          shadowStackEntryLoad, frame, false);
    }

    if (method != null) {
      return method.invoke(call, arguments);
    } else {
      return performDnu(arguments, rcvr, rcvrClass, selector, call);
    }
  }

  public static Object performDnu(final Object[] arguments, final Object rcvr,
      final SClass rcvrClass, final SSymbol selector,
      final IndirectCallNode call) {
    VM.callerNeedsToBeOptimized(
        "This method should only be called on the slow path. Use CachedDNUNode instead of this method.");

    if (TruffleOptions.AOT) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
    }

    if (VmSettings.DNU_PRINT_STACK_TRACE) {
      PrintStackTracePrim.printStackTrace(0, null);
      Output.errorPrintln("Lookup of " + selector + " failed in "
          + Types.getClassOf(rcvr).getName().getString());
    }

    // Won't use DNU caching here, because it is already a megamorphic node
    SArray argumentsArray = SArguments.getArgumentsWithoutReceiver(arguments);
    Object[] args = new Object[] {arguments[0], selector, argumentsArray};
    CallTarget target = CachedDnuNode.getDnu(rcvrClass, selector, SomLanguage.getVM(call));
    return call.call(target, args);
  }

  @Override
  public final int lengthOfDispatchChain() {
    return 1000;
  }

  @Override
  public final void collectDispatchStatistics(final Map<Invokable, Integer> result) {
    throw new NotYetImplementedException();
  }
}
