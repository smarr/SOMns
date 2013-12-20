package som.interpreter.nodes.messages;

import som.interpreter.Arguments.UnaryArguments;
import som.interpreter.Invokable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DefaultCallTarget;
import com.oracle.truffle.api.nodes.FrameFactory;
import com.oracle.truffle.api.nodes.InlinableCallSite;
import com.oracle.truffle.api.nodes.Node;

public abstract class UnarySendNode extends UnaryMessageNode {

  @Child protected ExpressionNode receiverExpr;

  private UnarySendNode(final SSymbol selector, final Universe universe,
      final ExpressionNode receiver) {
    super(selector, universe);
    this.receiverExpr = adoptChild(receiver);
  }

  private UnarySendNode(final UnarySendNode node) {
    this(node.selector, node.universe, node.receiverExpr);
  }

  @Override
  public ExpressionNode getReceiver() {
    return receiverExpr;
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    Object receiverValue = receiverExpr.executeGeneric(frame);
    return executeEvaluated(frame, receiverValue);
  }

  public static UnarySendNode create(final SSymbol selector,
      final Universe universe, final ExpressionNode receiver) {
    return new UninitializedSendNode(selector, universe, receiver, 0);
  }

  private static final class CachedSendNode extends UnarySendNode {

    @Child protected UnarySendNode    nextNode;

    // TODO: should this be an expression, or a unary message node??
    //       I am not to sure about the executeEvaluated if this can and should be a more general node type
    @Child protected UnaryMessageNode currentNode;     // 'inlined' node from the original method/call target
           private final SClass       cachedRcvrClass; // the receiver class is the classic PIC check criterion, and reasonably cheap

    CachedSendNode(final UnarySendNode node, final UnarySendNode next,
        final UnaryMessageNode current, final SClass rcvrClass) {
      super(node.selector, node.universe, node.receiverExpr);
      this.currentNode = adoptChild(current);
      this.nextNode    = adoptChild(next);
      this.cachedRcvrClass = rcvrClass;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      if (cachedRcvrClass == classOfReceiver(receiver)) {
        return currentNode.executeEvaluated(frame, receiver);
      } else {
        return nextNode.executeEvaluated(frame, receiver);
      }
    }
  }

  private static final class UninitializedSendNode extends UnarySendNode {
    protected final int depth;

    UninitializedSendNode(final SSymbol selector, final Universe universe,
        final ExpressionNode receiver, final int depth) {
      super(selector, universe, receiver);
      this.depth = depth;
    }

    UninitializedSendNode(final UninitializedSendNode node) {
      this(node, node.depth);
    }

    UninitializedSendNode(final UnarySendNode node, final int depth) {
      this(node.selector, node.universe, node.receiverExpr, depth);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      CompilerDirectives.transferToInterpreter();
      return specialize(receiver).executeEvaluated(frame, receiver);
    }

    private UnarySendNode specialize(final Object receiver) {
      CompilerAsserts.neverPartOfCompilation();

      if (depth < INLINE_CACHE_SIZE) {
        CallTarget  callTarget = lookupCallTarget(receiver);
        UnaryMessageNode current = (UnaryMessageNode) createCacheNode(callTarget);
        UnarySendNode       next = new UninitializedSendNode(this);
        return replace(new CachedSendNode(this,
            // TODO: understand when the cast fails, we might want to use some wrapper then
            //       in order to be able to use executeEvaluated in the CachedSendNode
            next, current, classOfReceiver(receiver)));
      } else {
        UnarySendNode topMost = (UnarySendNode) getTopNode();
        return topMost.replace(new GenericSendNode(this));
      }
    }

    protected Node getTopNode() {
      Node parentNode = this;
      for (int i = 0; i < depth; i++) {
        parentNode = parentNode.getParent();
      }
      return parentNode;
    }

    protected ExpressionNode createCacheNode(final CallTarget callTarget) {
      if (!(callTarget instanceof DefaultCallTarget)) {
        throw new RuntimeException("This should not happen in TruffleSOM");
      }

      DefaultCallTarget ct = (DefaultCallTarget) callTarget;
      Invokable invokable = (Invokable) ct.getRootNode();
      if (invokable.isAlwaysToBeInlined()) {
        return invokable.inline(callTarget, selector);
      } else {
        return new InlinableSendNode(this, ct);
      }
    }
  }

  private static final class InlinableSendNode extends UnarySendNode
    implements InlinableCallSite {

    private final DefaultCallTarget inlinableCallTarget;

    @CompilationFinal private int callCount;

    InlinableSendNode(final UnarySendNode node, final DefaultCallTarget callTarget) {
      super(node.selector, node.universe, node.receiverExpr);
      this.inlinableCallTarget = callTarget;
      callCount = 0;
    }

    @Override
    public int getCallCount() {
      return callCount;
    }

    @Override
    public void resetCallCount() {
      callCount = 0;
    }

    @Override
    public Node getInlineTree() {
      Invokable root = (Invokable) inlinableCallTarget.getRootNode();
      return root.getUninitializedBody();
    }

    @Override
    public boolean inline(final FrameFactory factory) {
      CompilerAsserts.neverPartOfCompilation();

      ExpressionNode method = null;
      Invokable invokable = (Invokable) inlinableCallTarget.getRootNode();
      method = invokable.inline(inlinableCallTarget, selector);
      if (method != null) {
        replace(method);
        return true;
      } else {
        return false;
      }
    }

    @Override
    public CallTarget getCallTarget() {
      return inlinableCallTarget;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      if (CompilerDirectives.inInterpreter()) {
        callCount++;
      }

      Invokable root = (Invokable) inlinableCallTarget.getRootNode();
      UnaryArguments args = new UnaryArguments(receiver,
          root.getNumberOfUpvalues(), universe.nilObject);
      return inlinableCallTarget.call(frame.pack(), args);
    }
  }

  private static final class GenericSendNode extends UnarySendNode {
    GenericSendNode(final UnarySendNode node) {
      super(node.selector, node.universe, node.receiverExpr);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      SMethod method = lookupMethod(receiver);
      return method.invoke(frame.pack(), receiver, universe);
    }
  }
}
