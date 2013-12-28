package som.interpreter.nodes.messages;

import som.interpreter.Arguments.BinaryArguments;
import som.interpreter.Invokable;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.specialized.IfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueMessageNodeFactory;
import som.interpreter.nodes.specialized.WhileFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.WhileTrueMessageNodeFactory;
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


public abstract class BinarySendNode extends BinaryMessageNode {

  @Child protected ExpressionNode   receiverExpr;
  @Child protected ExpressionNode   argumentNode;

  private BinarySendNode(final SSymbol selector,
      final Universe universe, final ExpressionNode receiver,
      final ExpressionNode argument) {
    super(selector, universe);
    this.receiverExpr = adoptChild(receiver);
    this.argumentNode = adoptChild(argument);
  }

  protected BinarySendNode(final BinarySendNode node) {
    this(node.selector, node.universe, node.receiverExpr, node.argumentNode);
  }

  @Override
  public ExpressionNode getReceiver() {
    return receiverExpr;
  }

  @Override
  public ExpressionNode getArgument() {
    return argumentNode;
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    Object receiverValue = receiverExpr.executeGeneric(frame);
    Object argument = argumentNode.executeGeneric(frame);
    return executeEvaluated(frame, receiverValue, argument);
  }

  public static BinarySendNode create(final SSymbol selector,
      final Universe universe, final ExpressionNode receiver,
      final ExpressionNode argument) {
    return new UninitializedSendNode(selector, universe, receiver, argument, 0);
  }

  private static final class CachedSendNode extends BinarySendNode {

    @Child protected BinarySendNode    nextNode;
    @Child protected BinaryMessageNode currentNode;
           private final SClass        cachedRcvrClass;

    CachedSendNode(final BinarySendNode node,
        final BinarySendNode next, final BinaryMessageNode current,
        final SClass rcvrClass) {
      super(node);
      this.nextNode        = adoptChild(next);
      this.currentNode     = adoptChild(current);
      this.cachedRcvrClass = rcvrClass;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object argument) {
      if (cachedRcvrClass == classOfReceiver(receiver)) {
        return currentNode.executeEvaluated(frame, receiver, argument);
      } else {
        return nextNode.executeEvaluated(frame, receiver, argument);
      }
    }
  }

  private static final class UninitializedSendNode extends BinarySendNode {

    protected final int depth;

    UninitializedSendNode(final SSymbol selector, final Universe universe,
        final ExpressionNode receiver, final ExpressionNode argument,
        final int depth) {
      super(selector, universe, receiver, argument);
      this.depth = depth;
    }

    UninitializedSendNode(final BinarySendNode node, final int depth) {
      super(node);
      this.depth = depth;
    }

    UninitializedSendNode(final UninitializedSendNode node) {
      this(node, node.depth);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver,
        final Object argument) {
      CompilerDirectives.transferToInterpreter();
      return specializeEvaluated(receiver, argument).executeEvaluated(frame, receiver, argument);
    }

    // DUPLICATED but types and specialized nodes
    private BinaryMessageNode specializeEvaluated(final Object receiver, final Object argument) {
      CompilerAsserts.neverPartOfCompilation();

      switch (selector.getString()) {
        case "whileTrue:":
          assert this == getTopNode();
          return replace(WhileTrueMessageNodeFactory.create(this, receiver, argument, receiverExpr, argumentNode));
        case "whileFalse:":
          assert this == getTopNode();
          return replace(WhileFalseMessageNodeFactory.create(this, receiver, argument, receiverExpr, argumentNode));
        case "ifTrue:":
          assert this == getTopNode();
          return replace(IfTrueMessageNodeFactory.create(this, receiver, argument, receiverExpr, argumentNode));
        case "ifFalse:":
          assert this == getTopNode();
          return replace(IfFalseMessageNodeFactory.create(this, receiver, argument, receiverExpr, argumentNode));
      }

      if (depth < INLINE_CACHE_SIZE) {
        CallTarget  callTarget = lookupCallTarget(receiver);
        BinaryMessageNode current = (BinaryMessageNode) createCachedNode(callTarget);
        BinarySendNode       next = new UninitializedSendNode(this);
        return replace(new CachedSendNode(this, next, current, classOfReceiver(receiver)));
      } else {
        BinarySendNode topMost = (BinarySendNode) getTopNode();
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
    protected ExpressionNode createCachedNode(final CallTarget callTarget) {
      if (!(callTarget instanceof DefaultCallTarget)) {
        throw new RuntimeException("This should not happen in TruffleSOM");
      }

      DefaultCallTarget ct = (DefaultCallTarget) callTarget;
      Invokable invokable = (Invokable) ct.getRootNode();
      if (invokable.isAlwaysToBeInlined()) {
        return invokable.inline(callTarget, selector);
      } else {
        return new InlinableSendNode(this, ct, invokable);
      }
    }
  }

  private static final class InlinableSendNode extends BinaryMessageNode
    implements InlinableCallSite {

    private final CallTarget inlinableCallTarget;
    private final Invokable  invokable;

    @CompilationFinal private int callCount;

    InlinableSendNode(final BinaryMessageNode node, final CallTarget callTarget,
        final Invokable invokable) {
      super(node);
      this.inlinableCallTarget = callTarget;
      this.invokable           = invokable;
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
      return invokable.getUninitializedBody();
    }

    @Override
    public boolean inline(final FrameFactory factory) {
      CompilerAsserts.neverPartOfCompilation();

      ExpressionNode method = null;
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
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ExpressionNode getArgument() { return null; }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object argument) {
      if (CompilerDirectives.inInterpreter()) {
        callCount++;
      }

      BinaryArguments args = new BinaryArguments(receiver, argument,
          invokable.getNumberOfUpvalues(), universe.nilObject);
      return inlinableCallTarget.call(frame.pack(), args);
    }
  }

  private static final class GenericSendNode extends BinarySendNode {
    GenericSendNode(final BinarySendNode node) {
      super(node);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object argument) {
      SMethod method = lookupMethod(receiver);
      return method.invoke(frame.pack(), receiver, argument, universe);
    }
  }
}
