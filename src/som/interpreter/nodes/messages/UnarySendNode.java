package som.interpreter.nodes.messages;

import static som.interpreter.TruffleCompiler.transferToInterpreter;
import som.interpreter.Arguments.UnaryArguments;
import som.interpreter.nodes.ClassCheckNode;
import som.interpreter.nodes.ClassCheckNode.Uninitialized;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.ISuperReadNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.CallNode;
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

    @Child private UnarySendNode    nextNode;

    // TODO: should this be an expression, or a unary message node??
    //       I am not to sure about the executeEvaluated if this can and should be a more general node type
    @Child private UnaryMessageNode currentNode;     // 'inlined' node from the original method/call target
    @Child private ClassCheckNode   cachedRcvrClassCheck; // the receiver class is the classic PIC check criterion, and reasonably cheap

    CachedSendNode(final UnarySendNode node, final UnarySendNode next,
        final UnaryMessageNode current, final SClass rcvrClass) {
      super(node.selector, node.universe, node.receiverExpr);
      this.currentNode = adoptChild(current);
      this.nextNode    = adoptChild(next);
      this.cachedRcvrClassCheck = adoptChild(new Uninitialized(rcvrClass,
          receiverExpr instanceof ISuperReadNode, universe));
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      if (cachedRcvrClassCheck.execute(receiver)) {
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
      transferToInterpreter("UninitializedUnarySendNode.specialize");
      return specialize(receiver).executeEvaluated(frame, receiver);
    }

    private UnarySendNode specialize(final Object receiver) {
      CompilerAsserts.neverPartOfCompilation();

      if (depth < INLINE_CACHE_SIZE) {
        RootCallTarget callTarget = lookupCallTarget(receiver);
        UnaryMessageNode current = (UnaryMessageNode) createCacheNode(callTarget);
        UnarySendNode       next = new UninitializedSendNode(this);
        return replace(new CachedSendNode(this, next, current,
            classOfReceiver(receiver)));
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

    protected ExpressionNode createCacheNode(final RootCallTarget callTarget) {
      return new InlinableUnarySendNode(this, callTarget);
    }
  }

  public static final class InlinableUnarySendNode extends UnaryMessageNode {

    private final CallNode inlinableNode;

    InlinableUnarySendNode(final UnarySendNode node,
        final CallTarget inlineableCallTarget) {
      this(node.selector, node.universe, inlineableCallTarget);
    }

    public InlinableUnarySendNode(final SSymbol selector, final Universe universe,
        final CallTarget inlinableCallTarget) {
      super(selector, universe);
      this.inlinableNode = adoptChild(Truffle.getRuntime().createCallNode(
          inlinableCallTarget));
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }
    @Override public ExpressionNode getReceiver() { return null; }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      UnaryArguments args = new UnaryArguments(receiver);
      return inlinableNode.call(frame.pack(), args);
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
