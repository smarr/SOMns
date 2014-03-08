//package som.interpreter.nodes.messages;
//
//import static som.interpreter.TruffleCompiler.transferToInterpreter;
//import som.interpreter.Arguments.TernaryArguments;
//import som.interpreter.TypesGen;
//import som.interpreter.nodes.ExpressionNode;
//import som.interpreter.nodes.ISuperReadNode;
//import som.interpreter.nodes.TernaryMessageNode;
//import som.interpreter.nodes.dispatch.ClassCheckNode;
//import som.interpreter.nodes.dispatch.ClassCheckNode.Uninitialized;
//import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeFactory;
//import som.interpreter.nodes.specialized.IntToDoMessageNodeFactory;
//import som.vm.Universe;
//import som.vmobjects.SBlock;
//import som.vmobjects.SClass;
//import som.vmobjects.SMethod;
//import som.vmobjects.SSymbol;
//
//import com.oracle.truffle.api.CallTarget;
//import com.oracle.truffle.api.CompilerAsserts;
//import com.oracle.truffle.api.RootCallTarget;
//import com.oracle.truffle.api.Truffle;
//import com.oracle.truffle.api.frame.VirtualFrame;
//import com.oracle.truffle.api.nodes.CallNode;
//import com.oracle.truffle.api.nodes.Node;
//
//
//public abstract class TernarySendNode extends TernaryMessageNode {
//

//    // DUPLICATED but types
//    private TernaryMessageNode specializeEvaluated(final Object receiver,
//        final Object firstArg, final Object secondArg) {
//      CompilerAsserts.neverPartOfCompilation();
//
//      switch (selector.getString()) {
//        case "ifTrue:ifFalse:":
//          assert this == getTopNode();
//          return replace(IfTrueIfFalseMessageNodeFactory.create(this, receiver, firstArg, secondArg, receiverExpr, firstArgNode, secondArgNode));
//        case "to:do:":
//          assert this == getTopNode();
//          if (TypesGen.TYPES.isImplicitInteger(receiver) &&
//              TypesGen.TYPES.isImplicitInteger(firstArg) &&
//              TypesGen.TYPES.isSBlock(secondArg)) {
//            return replace(IntToDoMessageNodeFactory.create(this, (SBlock) secondArg, receiverExpr, firstArgNode, secondArgNode));
//          }
//          break;
//      }
//
//      if (depth < INLINE_CACHE_SIZE) {
//        RootCallTarget  callTarget = lookupCallTarget(receiver);
//        TernaryMessageNode current = (TernaryMessageNode) createCachedNode(callTarget);
//        TernarySendNode       next = new UninitializedSendNode(this);
//        return replace(new CachedSendNode(this, next, current, classOfReceiver(receiver)));
//      } else {
//        TernarySendNode topMost = (TernarySendNode) getTopNode();
//        return topMost.replace(new GenericSendNode(this));
//      }
//    }
//
//    // DUPLICATED
//    protected Node getTopNode() {
//      Node parentNode = this;
//      for (int i = 0; i < depth; i++) {
//        parentNode = parentNode.getParent();
//      }
//      return parentNode;
//    }
//
//    // DUPLICATED but types
//    protected ExpressionNode createCachedNode(final RootCallTarget callTarget) {
//      return new InlinableSendNode(this, callTarget);
//    }
//  }
//
//  private static final class InlinableSendNode extends TernaryMessageNode {
//
//    private final CallNode inlinableNode;
//
//    InlinableSendNode(final TernaryMessageNode node, final CallTarget callTarget) {
//      super(node);
//      this.inlinableNode = adoptChild(Truffle.getRuntime().createCallNode(
//          callTarget));
//    }
//
//    @Override
//    public Object executeGeneric(final VirtualFrame frame) {
//      throw new IllegalStateException("executeGeneric() is not supported for these nodes, they always need to be called from a SendNode.");
//    }
//    @Override public ExpressionNode getReceiver()  { return null; }
//    @Override public ExpressionNode getFirstArg()  { return null; }
//    @Override public ExpressionNode getSecondArg() { return null; }
//
//    @Override
//    public Object executeEvaluated(final VirtualFrame frame,
//        final Object receiver, final Object argument1, final Object argument2) {
//      TernaryArguments args = new TernaryArguments(receiver, argument1, argument2);
//      return inlinableNode.call(frame.pack(), args);
//    }
//  }
//
//  private static final class GenericSendNode extends TernarySendNode {
//    GenericSendNode(final TernarySendNode node) {
//      super(node);
//    }
//
//    @Override
//    public Object executeEvaluated(final VirtualFrame frame,
//        final Object receiver, final Object argument1, final Object argument2) {
//      SMethod method = lookupMethod(receiver);
//      return method.invoke(frame.pack(), receiver,
//          argument1, argument2, universe);
//    }
//  }
//}
