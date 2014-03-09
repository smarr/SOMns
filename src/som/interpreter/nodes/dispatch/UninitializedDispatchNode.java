package som.interpreter.nodes.dispatch;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;


public class UninitializedDispatchNode extends AbstractDispatchWithLookupNode {

  public UninitializedDispatchNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final SArguments arguments) {
    transferToInterpreterAndInvalidate("Initialize a dispatch node.");

    // Determine position in dispatch node chain, i.e., size of inline cache
    Node i = this;
    int chainDepth = 0;
    while (i.getParent() instanceof AbstractDispatchNode) {
      i = i.getParent();
      chainDepth++;
    }
    GenericMessageSendNode sendNode = (GenericMessageSendNode) i.getParent();


    if (chainDepth < INLINE_CACHE_SIZE) {
      SClass rcvrClass = Types.getClassOf(arguments.getReceiver(), universe);
      SMethod method   = rcvrClass.lookupInvokable(selector);

      if (method != null) {
        UninitializedDispatchNode newChainEnd = new UninitializedDispatchNode(selector, universe);

        CachedDispatchNode initializedReplacement = new CachedDispatchNode(
            rcvrClass, method, newChainEnd, universe);
        return replace(initializedReplacement).executeDispatch(frame, arguments);
      }
      // if method == null: fall through and use generic node
    }

    // the chain is longer than the maximum defined by INLINE_CACHE_SIZE and
    // thus, this callsite is considered to be megaprophic, and we generalize
    // it.
    // Or, the lookup failed, and we have a callsite that leads to a
    // does not understand, which means, we also treat this callsite as
    // megamorphic.
    // TODO: see whether we could get #DNUs fast.
    GenericDispatchNode genericReplacement = new GenericDispatchNode(selector, universe);
    sendNode.replaceDispatchNodeBecauseCallSiteIsMegaMorphic(
          genericReplacement);
    return genericReplacement.executeDispatch(frame, arguments);
  }
}
