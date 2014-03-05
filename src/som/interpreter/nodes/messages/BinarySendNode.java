package som.interpreter.nodes.messages;

import static som.interpreter.TruffleCompiler.transferToInterpreter;
import som.interpreter.Arguments.BinaryArguments;
import som.interpreter.Invokable;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.ClassCheckNode;
import som.interpreter.nodes.ClassCheckNode.Uninitialized;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.ISuperReadNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.specialized.IfFalseMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueMessageNodeFactory;
import som.interpreter.nodes.specialized.WhileWithStaticBlocksNode.WhileFalseStaticBlocksNode;
import som.interpreter.nodes.specialized.WhileWithStaticBlocksNode.WhileTrueStaticBlocksNode;
import som.vm.Universe;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
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

    @Child private BinarySendNode    nextNode;
    @Child private BinaryMessageNode currentNode;
    @Child private ClassCheckNode    cachedRcvrClassCheck;

    CachedSendNode(final BinarySendNode node,
        final BinarySendNode next, final BinaryMessageNode current,
        final SClass rcvrClass) {
      super(node);
      this.nextNode        = adoptChild(next);
      this.currentNode     = adoptChild(current);
      this.cachedRcvrClassCheck = adoptChild(new Uninitialized(rcvrClass,
          receiverExpr instanceof ISuperReadNode, universe));
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object argument) {

      if (cachedRcvrClassCheck.execute(receiver)) {
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
      transferToInterpreter("UninitializedBinarySendNode.specialize");
      return specializeEvaluated(receiver, argument).executeEvaluated(frame, receiver, argument);
    }

    // DUPLICATED but types and specialized nodes
    private BinaryMessageNode specializeEvaluated(final Object receiver, final Object argument) {
      CompilerAsserts.neverPartOfCompilation();

      switch (selector.getString()) {
        case "whileTrue:":
          assert this == getTopNode();
          if (getArgument() instanceof BlockNode &&
              getReceiver() instanceof BlockNode) {
            BlockNode argBlockNode = (BlockNode) getArgument();
            SBlock    argBlock     = (SBlock)    argument;
            return replace(new WhileTrueStaticBlocksNode(this,
                (BlockNode) getReceiver(), argBlockNode, (SBlock) receiver, argBlock));
          }
          break; // use normal send
        case "whileFalse:":
          assert this == getTopNode();
          if (getArgument() instanceof BlockNode &&
              getReceiver() instanceof BlockNode) {
            BlockNode argBlockNode = (BlockNode) getArgument();
            SBlock    argBlock     = (SBlock)    argument;
            return replace(new WhileFalseStaticBlocksNode(this,
                (BlockNode) getReceiver(), argBlockNode,
                (SBlock) receiver, argBlock));
          }
          break; // use normal send
        case "ifTrue:":
          assert this == getTopNode();
          return replace(IfTrueMessageNodeFactory.create(this, receiver, argument, receiverExpr, argumentNode));
        case "ifFalse:":
          assert this == getTopNode();
          return replace(IfFalseMessageNodeFactory.create(this, receiver, argument, receiverExpr, argumentNode));
      }

      if (depth < INLINE_CACHE_SIZE) {
        RootCallTarget callTarget = lookupCallTarget(receiver);
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
    protected ExpressionNode createCachedNode(final RootCallTarget callTarget) {
      Invokable invokable = (Invokable) callTarget.getRootNode();
      if (invokable.isAlwaysToBeInlined()) {
        return invokable.inline(callTarget, selector);
      } else {
        return new InlinableBinarySendNode(this, callTarget, invokable);
      }
    }
  }

  public static final class InlinableBinarySendNode extends BinaryMessageNode {

    private final RootCallTarget inlinableCallTarget;
//    private final Invokable  invokable;

    public InlinableBinarySendNode(final BinaryMessageNode node, final RootCallTarget callTarget,
        final Invokable invokable) {
      super(node);
      this.inlinableCallTarget = callTarget;
//      this.invokable           = invokable;
    }

    public InlinableBinarySendNode(final SSymbol selector, final Universe universe,
        final RootCallTarget callTarget, final Invokable invokable) {
      super(selector, universe);
      this.inlinableCallTarget = callTarget;
//      this.invokable           = invokable;
    }

//    @Override
//    public Node getInlineTree() {
//      return invokable.getUninitializedBody();
//    }

//    @Override
//    public boolean inline(final FrameFactory factory) {
//      CompilerAsserts.neverPartOfCompilation();
//
//      ExpressionNode method = null;
//      method = invokable.inline(inlinableCallTarget, selector);
//      if (method != null) {
//        replace(method);
//        return true;
//      } else {
//        return false;
//      }
//    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
    }
    @Override public ExpressionNode getReceiver() { return null; }
    @Override public ExpressionNode getArgument() { return null; }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final Object receiver, final Object argument) {
//      if (CompilerDirectives.inInterpreter()) {
//        callCount =+ 10;
//      }

      BinaryArguments args = new BinaryArguments(receiver, argument);
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
