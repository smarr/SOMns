package som.interpreter.nodes.messages;

import static som.interpreter.TruffleCompiler.transferToInterpreter;
import som.interpreter.Arguments.KeywordArguments;
import som.interpreter.nodes.ArgumentEvaluationNode;
import som.interpreter.nodes.ClassCheckNode;
import som.interpreter.nodes.ClassCheckNode.Uninitialized;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.ISuperReadNode;
import som.interpreter.nodes.KeywordMessageNode;
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


public abstract class KeywordSendNode extends KeywordMessageNode {

  @Child protected ExpressionNode receiverExpr;
  @Child protected ArgumentEvaluationNode argumentsNode;

  private KeywordSendNode(final SSymbol selector,
      final Universe universe, final ExpressionNode receiver,
      final ArgumentEvaluationNode arguments) {
    super(selector, universe);
    this.receiverExpr  = adoptChild(receiver);
    this.argumentsNode = adoptChild(arguments);
  }

  private KeywordSendNode(final KeywordSendNode node) {
    this(node.selector, node.universe, node.receiverExpr, node.argumentsNode);
  }

  @Override
  public ExpressionNode getReceiver() {
    return receiverExpr;
  }

  @Override
  public ArgumentEvaluationNode getArguments() {
    return argumentsNode;
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    Object receiverValue = receiverExpr.executeGeneric(frame);
    Object[] args = argumentsNode.executeArray(frame);
    return executeEvaluated(frame, receiverValue, args);
  }

  public static KeywordSendNode create(final SSymbol selector,
      final Universe universe, final ExpressionNode receiver,
      final ArgumentEvaluationNode arguments) {
    return new UninitializedSendNode(selector, universe, receiver, arguments, 0);
  }

  private static final class CachedSendNode extends KeywordSendNode {

    @Child protected KeywordSendNode    nextNode;
    @Child protected KeywordMessageNode currentNode;
    @Child private ClassCheckNode       cachedRcvrClassCheck;

    CachedSendNode(final KeywordSendNode node,
        final KeywordSendNode next, final KeywordMessageNode current,
        final SClass rcvrClass) {
      super(node);
      this.nextNode        = adoptChild(next);
      this.currentNode     = adoptChild(current);
      this.cachedRcvrClassCheck = adoptChild(new Uninitialized(rcvrClass,
          receiverExpr instanceof ISuperReadNode, universe));
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object[] arguments) {
      if (cachedRcvrClassCheck.execute(receiver)) {
        return currentNode.executeEvaluated(frame, receiver, arguments);
      } else {
        return nextNode.executeEvaluated(frame, receiver, arguments);
      }
    }
  }

  private static final class UninitializedSendNode extends KeywordSendNode {

    protected final int depth;

    UninitializedSendNode(final SSymbol selector, final Universe universe,
        final ExpressionNode receiver, final ArgumentEvaluationNode arguments,
        final int depth) {
      super(selector, universe, receiver, arguments);
      this.depth = depth;
    }

    UninitializedSendNode(final KeywordSendNode node, final int depth) {
      super(node);
      this.depth = depth;
    }

    UninitializedSendNode(final UninitializedSendNode node) {
      this(node, node.depth);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver,
        final Object[] arguments) {
      transferToInterpreter("UninitializedKeywordSendNode.specialize");
      return specialize(receiver).executeEvaluated(frame, receiver, arguments);
    }

    // DUPLICATED but types
    private KeywordSendNode specialize(final Object receiver) {
      CompilerAsserts.neverPartOfCompilation();

      if (depth < INLINE_CACHE_SIZE) {
        RootCallTarget  callTarget = lookupCallTarget(receiver);
        KeywordMessageNode current = (KeywordMessageNode) createCachedNode(callTarget);
        KeywordSendNode       next = new UninitializedSendNode(this);
        return replace(new CachedSendNode(this, next, current, classOfReceiver(receiver)));
      } else {
        KeywordSendNode topMost = (KeywordSendNode) getTopNode();
        return topMost.replace(new GenericSendNode(this));
      }
    }

    // DUPLICATED
    protected Node getTopNode() {
      Node parentNode = this;
      for (int i = 0; i < depth; i++) {
        parentNode = parentNode.getParent();
      }
      return parentNode;
    }

    // DUPLICATED but types
    protected ExpressionNode createCachedNode(final RootCallTarget callTarget) {
      return new InlinableSendNode(this, callTarget);
    }
  }

  private static final class InlinableSendNode extends KeywordMessageNode {

    private final CallNode inlinableNode;

    InlinableSendNode(final KeywordMessageNode node, final CallTarget callTarget) {
      super(node);
      this.inlinableNode = adoptChild(Truffle.getRuntime().createCallNode(
          callTarget));
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ArgumentEvaluationNode getArguments() { return null; }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object[] arguments) {
      KeywordArguments args = new KeywordArguments(receiver, arguments);
      return inlinableNode.call(frame.pack(), args);
    }
  }

  private static final class GenericSendNode extends KeywordSendNode {
    GenericSendNode(final KeywordSendNode node) {
      super(node);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver, final Object[] arguments) {
      SMethod method = lookupMethod(receiver);
      return method.invoke(frame.pack(), receiver, arguments, universe);
    }
  }
}
