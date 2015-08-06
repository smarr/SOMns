package som.interpreter.nodes.dispatch;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.compiler.AccessModifier;
import som.interpreter.Types;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.interpreter.nodes.SOMNode;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithoutFields;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public final class UninitializedDispatchNode extends AbstractDispatchWithLookupNode {

  private final AccessModifier minimalVisibility;

  public UninitializedDispatchNode(final SSymbol selector,
      final AccessModifier minimalVisibility) {
    super(selector);
    assert minimalVisibility == AccessModifier.PROTECTED
        || minimalVisibility == AccessModifier.PUBLIC;
    this.minimalVisibility = minimalVisibility;
  }

  private AbstractDispatchNode specialize(final Object[] arguments) {
    transferToInterpreterAndInvalidate("Initialize a dispatch node.");
    Object rcvr = arguments[0];

    assert rcvr != null;

    // Determine position in dispatch node chain, i.e., size of inline cache
    Node i = this;
    int chainDepth = 0;
    while (i.getParent() instanceof AbstractDispatchNode) {
      i = i.getParent();
      chainDepth++;
    }
    GenericMessageSendNode sendNode = (GenericMessageSendNode) i.getParent();

    if (chainDepth < INLINE_CACHE_SIZE) {
      SClass rcvrClass = Types.getClassOf(rcvr);
      Dispatchable dispatchable = rcvrClass.lookupMessage(selector, minimalVisibility);

      if (dispatchable != null) {
        assert dispatchable.getAccessModifier() != AccessModifier.PRIVATE;
      }

      UninitializedDispatchNode newChainEnd = new UninitializedDispatchNode(
          selector, minimalVisibility);

      if (rcvr instanceof SObjectWithoutFields) {
        AbstractDispatchNode node;
        if (dispatchable != null) {
          node = dispatchable.getDispatchNode(rcvr, rcvrClass, newChainEnd);
        } else {
          node = new CachedDnuSObjectCheckNode(rcvrClass, selector, newChainEnd);
        }

        if ((getParent() instanceof CachedDispatchSObjectCheckNode)) {
          return replace(node);
        } else {
          SObjectCheckDispatchNode checkNode = new SObjectCheckDispatchNode(node,
              new UninitializedDispatchNode(selector, minimalVisibility));
          return replace(checkNode);
        }
      } else {
        AbstractDispatchNode next = sendNode.getDispatchListHead();

        AbstractDispatchNode node;
        if (dispatchable == null) {
          node = new CachedDnuSimpleCheckNode(
              rcvr.getClass(), rcvrClass, selector, next);
        } else {
          node = dispatchable.getDispatchNode(rcvr, rcvr.getClass(), next);
        }

        // the simple checks are prepended
        sendNode.adoptNewDispatchListHead(node);
        return node;
      }
    }

    // the chain is longer than the maximum defined by INLINE_CACHE_SIZE and
    // thus, this callsite is considered to be megamorphic, and we generalize
    // it.
    GenericDispatchNode genericReplacement = new GenericDispatchNode(selector,
        minimalVisibility, null);
    sendNode.replaceDispatchListHead(genericReplacement);
    return genericReplacement;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame, final Object[] arguments) {
    RootNode root = SOMNode.getRootNodeAndTryReallyHard(this);
    assert root != null;

    AbstractDispatchNode newNode;
    // we modify a dispatch chain here, so, better grab the root node before we do anything
    synchronized (root) {
      newNode = specialize(arguments);
    }

    return newNode.executeDispatch(frame, arguments);
  }

  @Override
  public int lengthOfDispatchChain() {
    return 0;
  }

}
