package som.interpreter.nodes.dispatch;

import som.compiler.AccessModifier;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;

public final class GenericDispatchNode extends AbstractDispatchWithLookupNode {
  @Child private IndirectCallNode call;
  private final AccessModifier minimalVisibility;

  public GenericDispatchNode(final SSymbol selector, final AccessModifier minimalAccess) {
    super(selector);
    this.minimalVisibility = minimalAccess;
    call = Truffle.getRuntime().createIndirectCallNode();
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    Object rcvr = arguments[0];
    SClass rcvrClass = Types.getClassOf(rcvr);
    Dispatchable method = rcvrClass.lookupMessage(selector, minimalVisibility);

    CallTarget target;
    Object[] args;

    if (method != null) {
      target = method.getCallTargetIfAvailable();
      args = arguments;
    } else {
      // Won't use DNU caching here, because it is already a megamorphic node
      SArray argumentsArray = SArguments.getArgumentsWithoutReceiver(arguments);
      args = new Object[] {arguments[0], selector, argumentsArray};
      target = AbstractCachedDnuNode.getDnu(rcvrClass);
    }
    return call.call(frame, target, args);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1000;
  }
}
