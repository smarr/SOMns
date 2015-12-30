package som.interpreter.nodes.dispatch;

import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;


public final class GenericDispatchNode extends AbstractDispatchNode {
  @Child private IndirectCallNode call;
  private final AccessModifier minimalVisibility;
  private final MixinDefinitionId mixinId;
  private final SSymbol selector;

  public GenericDispatchNode(final SSymbol selector,
      final AccessModifier minimalAccess, final MixinDefinitionId mixinId) {
    this.selector = selector;
    assert minimalAccess.ordinal() >= AccessModifier.PROTECTED.ordinal() || mixinId != null;
    this.minimalVisibility = minimalAccess;
    this.mixinId = mixinId;
    call = Truffle.getRuntime().createIndirectCallNode();
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    Object rcvr = arguments[0];
    SClass rcvrClass = Types.getClassOf(rcvr);
    Dispatchable method = doLookup(rcvrClass);

    if (method != null) {
      return method.invoke(call, frame, arguments);
    } else {
      // Won't use DNU caching here, because it is already a megamorphic node
      SArray argumentsArray = SArguments.getArgumentsWithoutReceiver(arguments);
      Object[] args = new Object[] {arguments[0], selector, argumentsArray};
      CallTarget target = CachedDnuNode.getDnu(rcvrClass);
      return call.call(frame, target, args);
    }
  }

  @TruffleBoundary
  private Dispatchable doLookup(final SClass rcvrClass) {
    Dispatchable method;

    if (mixinId != null) {
      method = rcvrClass.lookupPrivate(selector, mixinId);
    } else {
      method = rcvrClass.lookupMessage(selector, minimalVisibility);
    }
    return method;
  }

  @Override
  public int lengthOfDispatchChain() {
    return 1000;
  }
}
