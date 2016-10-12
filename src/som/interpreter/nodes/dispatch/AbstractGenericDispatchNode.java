package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.VmSettings;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.primitives.SystemPrims.PrintStackTracePrim;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;


public abstract class AbstractGenericDispatchNode extends AbstractDispatchNode {
  @Child protected IndirectCallNode call;
  protected final SSymbol selector;

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

    if (method != null) {
      return method.invoke(call, frame, arguments);
    } else {
      return performDnu(frame, arguments, rcvr, rcvrClass, selector, call);
    }
  }

  public static Object performDnu(final VirtualFrame frame,
      final Object[] arguments, final Object rcvr, final SClass rcvrClass,
      final SSymbol selector, final IndirectCallNode call) {
    if (VmSettings.DNU_PRINT_STACK_TRACE) {
      PrintStackTracePrim.printStackTrace();
      VM.errorPrintln("Lookup of " + selector + " failed in " + Types.getClassOf(rcvr).getName().getString());
    }

    // Won't use DNU caching here, because it is already a megamorphic node
    SArray argumentsArray = SArguments.getArgumentsWithoutReceiver(arguments);
    Object[] args = new Object[] {arguments[0], selector, argumentsArray};
    CallTarget target = CachedDnuNode.getDnu(rcvrClass);
    return call.call(frame, target, args);
  }

  @Override
  public final int lengthOfDispatchChain() {
    return 1000;
  }
}
