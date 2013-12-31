package som.interpreter.nodes.messages;

import static som.interpreter.TruffleCompiler.transferToInterpreter;
import som.interpreter.Arguments.TernaryArguments;
import som.interpreter.Invokable;
import som.interpreter.nodes.ClassCheckNode;
import som.interpreter.nodes.ClassCheckNode.Uninitialized;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SelfReadNode.SuperReadNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
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


public abstract class TernarySendNode extends TernaryMessageNode {

  @Child protected ExpressionNode receiverExpr;
  @Child protected ExpressionNode firstArgNode;
  @Child protected ExpressionNode secondArgNode;

  private TernarySendNode(final SSymbol selector,
      final Universe universe, final ExpressionNode receiver,
      final ExpressionNode firstArg, final ExpressionNode secondArg) {
    super(selector, universe);
    this.receiverExpr  = adoptChild(receiver);
    this.firstArgNode  = adoptChild(firstArg);
    this.secondArgNode = adoptChild(secondArg);
  }

  protected TernarySendNode(final TernarySendNode node) {
    this(node.selector, node.universe, node.receiverExpr, node.firstArgNode, node.secondArgNode);
  }

  @Override
  public ExpressionNode getReceiver() {
    return receiverExpr;
  }

  @Override
  public ExpressionNode getFirstArg() {
    return firstArgNode;
  }

  @Override
  public ExpressionNode getSecondArg() {
    return secondArgNode;
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    Object receiverValue = receiverExpr.executeGeneric(frame);
    Object argument1 = firstArgNode.executeGeneric(frame);
    Object argument2 = secondArgNode.executeGeneric(frame);
    return executeEvaluated(frame, receiverValue, argument1, argument2);
  }

  public static TernarySendNode create(final SSymbol selector,
      final Universe universe, final ExpressionNode receiver,
      final ExpressionNode firstArg, final ExpressionNode secondArg) {
    return new UninitializedSendNode(selector, universe, receiver, firstArg, secondArg, 0);
  }

  private static final class CachedSendNode extends TernarySendNode {

    @Child private TernarySendNode    nextNode;
    @Child private TernaryMessageNode currentNode;
    @Child private ClassCheckNode     cachedRcvrClassCheck;

    CachedSendNode(final TernarySendNode node,
        final TernarySendNode next, final TernaryMessageNode current,
        final SClass rcvrClass) {
      super(node);
      this.nextNode        = adoptChild(next);
      this.currentNode     = adoptChild(current);
      this.cachedRcvrClassCheck = adoptChild(new Uninitialized(rcvrClass,
          receiverExpr instanceof SuperReadNode, universe));
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object argument1, final Object argument2) {
      if (cachedRcvrClassCheck.execute(receiver)) {
        return currentNode.executeEvaluated(frame, receiver, argument1, argument2);
      } else {
        return nextNode.executeEvaluated(frame, receiver, argument1, argument2);
      }
    }
  }

  private static final class UninitializedSendNode extends TernarySendNode {

    protected final int depth;

    UninitializedSendNode(final SSymbol selector, final Universe universe,
        final ExpressionNode receiver, final ExpressionNode firstArg,
        final ExpressionNode secondArg, final int depth) {
      super(selector, universe, receiver, firstArg, secondArg);
      this.depth = depth;
    }

    UninitializedSendNode(final TernarySendNode node, final int depth) {
      super(node);
      this.depth = depth;
    }

    UninitializedSendNode(final UninitializedSendNode node) {
      this(node, node.depth);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver,
        final Object argument1, final Object argument2) {
      transferToInterpreter("UninitializedTernarySendNode.specialize");
      return specializeEvaluated(receiver, argument1, argument2).executeEvaluated(
          frame, receiver, argument1, argument2);
    }

    // DUPLICATED but types
    private TernaryMessageNode specializeEvaluated(final Object receiver,
        final Object firstArg, final Object secondArg) {
      CompilerAsserts.neverPartOfCompilation();

      switch (selector.getString()) {
        case "ifTrue:ifFalse:":
          assert this == getTopNode();
          return replace(IfTrueIfFalseMessageNodeFactory.create(this, receiver, firstArg, secondArg, receiverExpr, firstArgNode, secondArgNode));
      }

      if (depth < INLINE_CACHE_SIZE) {
        CallTarget  callTarget = lookupCallTarget(receiver);
        TernaryMessageNode current = (TernaryMessageNode) createCachedNode(callTarget);
        TernarySendNode       next = new UninitializedSendNode(this);
        return replace(new CachedSendNode(this, next, current, classOfReceiver(receiver)));
      } else {
        TernarySendNode topMost = (TernarySendNode) getTopNode();
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

  private static final class InlinableSendNode extends TernaryMessageNode
    implements InlinableCallSite {

    private final CallTarget inlinableCallTarget;
    private final Invokable  invokable;

    @CompilationFinal private int callCount;

    InlinableSendNode(final TernaryMessageNode node, final DefaultCallTarget callTarget,
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
    @Override public ExpressionNode getReceiver()  { return null; }
    @Override public ExpressionNode getFirstArg()  { return null; }
    @Override public ExpressionNode getSecondArg() { return null; }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object argument1, final Object argument2) {
      if (CompilerDirectives.inInterpreter()) {
        callCount =+ 10;
      }

      TernaryArguments args = new TernaryArguments(receiver, argument1,
          argument2, invokable.getNumberOfUpvalues(), universe.nilObject);
      return inlinableCallTarget.call(frame.pack(), args);
    }
  }

  private static final class GenericSendNode extends TernarySendNode {
    GenericSendNode(final TernarySendNode node) {
      super(node);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object argument1, final Object argument2) {
      SMethod method = lookupMethod(receiver);
      return method.invoke(frame.pack(), receiver,
          argument1, argument2, universe);
    }
  }
}
