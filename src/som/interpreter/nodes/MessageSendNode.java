package som.interpreter.nodes;

import static som.interpreter.nodes.SOMNode.unwrapIfNecessary;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.VmSettings;
import som.compiler.AccessModifier;
import som.instrumentation.MessageSendNodeWrapper;
import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.dispatch.GenericDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.nodes.literals.BlockNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.EagerBinaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerTernaryPrimitiveNode;
import som.interpreter.nodes.nary.EagerUnaryPrimitiveNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.nodes.specialized.AndMessageNodeFactory;
import som.interpreter.nodes.specialized.AndMessageNodeFactory.AndBoolMessageNodeFactory;
import som.interpreter.nodes.specialized.IfTrueIfFalseMessageNodeGen;
import som.interpreter.nodes.specialized.IntDownToDoMessageNodeGen;
import som.interpreter.nodes.specialized.IntToByDoMessageNode;
import som.interpreter.nodes.specialized.IntToDoMessageNodeGen;
import som.interpreter.nodes.specialized.OrMessageNodeGen;
import som.interpreter.nodes.specialized.OrMessageNodeGen.OrBoolMessageNodeGen;
import som.interpreter.nodes.specialized.whileloops.WhileWithDynamicBlocksNode;
import som.primitives.MethodPrimsFactory.InvokeOnPrimFactory;
import som.primitives.arrays.ToArgumentsArrayNodeGen;
import som.vm.NotYetImplementedException;
import som.vm.Primitives;
import som.vm.Primitives.PrimAndFact;
import som.vmobjects.SBlock;
import som.vmobjects.SSymbol;
import tools.dym.Tags.VirtualInvoke;


public final class MessageSendNode {

  public static AbstractMessageSendNode createMessageSend(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source) {
    return new UninitializedMessageSendNode(selector, arguments, source);
  }

  public static AbstractMessageSendNode adaptSymbol(final SSymbol newSelector,
      final AbstractMessageSendNode node) {
    assert node instanceof UninitializedMessageSendNode;
    return new UninitializedMessageSendNode(newSelector, node.argumentNodes,
        node.getSourceSection());
  }

  public static AbstractMessageSendNode createForPerformNodes(
      final SSymbol selector, final SourceSection source) {
    return new UninitializedSymbolSendNode(selector, source);
  }

  public static GenericMessageSendNode createGeneric(final SSymbol selector,
      final ExpressionNode[] argumentNodes, final SourceSection source) {
    if (argumentNodes != null &&
        unwrapIfNecessary(argumentNodes[0]) instanceof ISpecialSend) {
      throw new NotYetImplementedException();
    } else {
      return new GenericMessageSendNode(selector, argumentNodes,
          UninitializedDispatchNode.createRcvrSend(source, selector, AccessModifier.PUBLIC),
          source);
    }
  }

  public abstract static class AbstractMessageSendNode extends ExprWithTagsNode
      implements PreevaluatedExpression {

    @Children protected final ExpressionNode[] argumentNodes;

    protected AbstractMessageSendNode(final ExpressionNode[] arguments,
        final SourceSection source) {
      super(source);
      this.argumentNodes = arguments;
    }

    protected AbstractMessageSendNode(final SourceSection source) {
      super(source);
      // default constructor for instrumentation wrapper nodes
      this.argumentNodes = null;
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == StandardTags.CallTag.class) {
        return true;
      }
      return super.isTaggedWith(tag);
    }

    public boolean isSpecialSend() {
      return unwrapIfNecessary(argumentNodes[0]) instanceof ISpecialSend;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = evaluateArguments(frame);
      return doPreEvaluated(frame, arguments);
    }

    @ExplodeLoop
    private Object[] evaluateArguments(final VirtualFrame frame) {
      Object[] arguments = new Object[argumentNodes.length];
      for (int i = 0; i < argumentNodes.length; i++) {
        arguments[i] = argumentNodes[i].executeGeneric(frame);
        assert arguments[i] != null;
      }
      return arguments;
    }
  }

  public abstract static class AbstractUninitializedMessageSendNode
      extends AbstractMessageSendNode {

    protected final SSymbol selector;

    protected AbstractUninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final SourceSection source) {
      super(arguments, source);
      this.selector = selector;
    }

    @Override
    public String toString() {
      return "UninitMsgSend(" + selector.toString() + ")";
    }

    public SSymbol getSelector() {
      return selector;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      // This is a branch never taken, none of the code here should be compiled.
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return super.executeGeneric(frame);
    }

    @Override
    public final Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      // This is a branch never taken, none of the code here should be compiled.
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return specialize(arguments).
          doPreEvaluated(frame, arguments);
    }

    private PreevaluatedExpression specialize(final Object[] arguments) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Specialize Message Node");

      Primitives prims = VM.getVM().getPrimitives();

      Object receiver = arguments[0];
      PrimAndFact prim = prims.getFactoryForEagerSpecialization(selector, receiver, argumentNodes);

      // let's organize the specializations by number of arguments
      // perhaps not the best, but simple
      switch (argumentNodes.length) {
        case  1: return specializeUnary(prim, arguments);
        case  2: return specializeBinary(prim, arguments);
        case  3: return specializeTernary(prim, arguments);
        case  4: return specializeQuaternary(prim, arguments);
      }
      return makeSend();
    }

    protected PreevaluatedExpression makeSend() {
      // first option is a super send, super sends are treated specially because
      // the receiver class is lexically determined
      if (isSpecialSend()) {
        return makeSpecialSend();
      }
      return makeOrdenarySend();
    }

    protected abstract PreevaluatedExpression makeSpecialSend();
    protected abstract GenericMessageSendNode makeOrdenarySend();

    private PreevaluatedExpression makeEagerUnaryPrim(final UnaryExpressionNode prim) {
      VM.insertInstrumentationWrapper(this);
      assert prim.getSourceSection() != null;

      unwrapIfNecessary(argumentNodes[0]).markAsPrimitiveArgument();
      PreevaluatedExpression result = replace(new EagerUnaryPrimitiveNode(
          prim.getSourceSection(), selector, argumentNodes[0], prim));
      VM.insertInstrumentationWrapper((Node) result);
      VM.insertInstrumentationWrapper(argumentNodes[0]);
      return result;
    }

    protected PreevaluatedExpression specializeUnary(final PrimAndFact prim,
        final Object[] args) {
      // TODO: remove null check, and better, get this stuff somehow realized polymorphically
      if (prim != null) {
        UnaryExpressionNode newNode = prim.createNode(args, argumentNodes, getSourceSection());
        if (prim.noWrapper()) {
          return replace(newNode);
        } else {
          return makeEagerUnaryPrim(newNode);
        }
      }
      return makeSend();
    }

    private PreevaluatedExpression makeEagerBinaryPrim(final BinaryExpressionNode prim) {
      VM.insertInstrumentationWrapper(this);
      assert prim.getSourceSection() != null;

      unwrapIfNecessary(argumentNodes[0]).markAsPrimitiveArgument();
      unwrapIfNecessary(argumentNodes[1]).markAsPrimitiveArgument();

      PreevaluatedExpression result = replace(new EagerBinaryPrimitiveNode(
          selector, argumentNodes[0], argumentNodes[1], prim, prim.getSourceSection()));
      VM.insertInstrumentationWrapper((Node) result);
      VM.insertInstrumentationWrapper(argumentNodes[0]);
      VM.insertInstrumentationWrapper(argumentNodes[1]);
      return result;
    }

    protected PreevaluatedExpression specializeBinary(final PrimAndFact prim, final Object[] arguments) {
      if (prim != null) {
        assert !prim.noWrapper();
        return makeEagerBinaryPrim(prim.createNode(arguments, argumentNodes, getSourceSection()));
      }

      switch (selector.getString()) {
        case "and:":
        case "&&":
          if (arguments[0] instanceof Boolean) {
            if (unwrapIfNecessary(argumentNodes[1]) instanceof BlockNode) {
              return replace(AndMessageNodeFactory.create((SBlock) arguments[1],
                  getSourceSection(), argumentNodes[0], argumentNodes[1]));
            } else if (arguments[1] instanceof Boolean) {
              return replace(AndBoolMessageNodeFactory.create(getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            }
          }
          break;
        case "or:":
        case "||":
          if (arguments[0] instanceof Boolean) {
            if (unwrapIfNecessary(argumentNodes[1]) instanceof BlockNode) {
              return replace(OrMessageNodeGen.create((SBlock) arguments[1],
                  getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            } else if (arguments[1] instanceof Boolean) {
              return replace(OrBoolMessageNodeGen.create(
                  getSourceSection(),
                  argumentNodes[0], argumentNodes[1]));
            }
          }
          break;
// TODO: this is not a correct primitive, new an UnequalsUnequalsPrim...
//        case "~=":
//          return replace(new EagerBinaryPrimitiveNode(selector, argumentNodes[0],
//              argumentNodes[1],
//              UnequalsPrimFactory.create(null, null)));

      }
      return makeSend();
    }

    private PreevaluatedExpression makeEagerTernaryPrim(final TernaryExpressionNode prim) {
      VM.insertInstrumentationWrapper(this);
      assert prim.getSourceSection() != null;

      unwrapIfNecessary(argumentNodes[0]).markAsPrimitiveArgument();
      unwrapIfNecessary(argumentNodes[1]).markAsPrimitiveArgument();
      unwrapIfNecessary(argumentNodes[2]).markAsPrimitiveArgument();

      PreevaluatedExpression result = replace(new EagerTernaryPrimitiveNode(
          prim.getSourceSection(), selector, argumentNodes[0],
          argumentNodes[1], argumentNodes[2], prim));

      VM.insertInstrumentationWrapper((Node) result);
      VM.insertInstrumentationWrapper(argumentNodes[0]);
      VM.insertInstrumentationWrapper(argumentNodes[1]);
      VM.insertInstrumentationWrapper(argumentNodes[2]);
      return result;
    }

    protected PreevaluatedExpression specializeTernary(final PrimAndFact prim,
        final Object[] arguments) {
      if (prim != null) {
        TernaryExpressionNode node = prim.createNode(arguments, argumentNodes, getSourceSection());
        if (prim.noWrapper()) {
          return replace(node);
        } else {
          return makeEagerTernaryPrim(node);
        }
      }

      switch (selector.getString()) {
        case "ifTrue:ifFalse:":
          return replace(IfTrueIfFalseMessageNodeGen.create(getSourceSection(),
              arguments[0], arguments[1], arguments[2], argumentNodes[0],
              argumentNodes[1], argumentNodes[2]));
        case "to:do:":
          if (!VmSettings.DYNAMIC_METRICS &&
              arguments[0] instanceof Long &&
              (arguments[1] instanceof Long ||
                  arguments[1] instanceof Double) &&
              arguments[2] instanceof SBlock) {
            return replace(IntToDoMessageNodeGen.create(getSourceSection(),
                argumentNodes[0], argumentNodes[1], argumentNodes[2]));
          }
          break;
        case "downTo:do:":
          if (!VmSettings.DYNAMIC_METRICS &&
              arguments[0] instanceof Long &&
              (arguments[1] instanceof Long ||
                  arguments[1] instanceof Double) &&
              arguments[2] instanceof SBlock) {
            return replace(IntDownToDoMessageNodeGen.create(this,
                (SBlock) arguments[2], argumentNodes[0], argumentNodes[1],
                argumentNodes[2]));
          }
          break;
        case "invokeOn:with:":
          return replace(InvokeOnPrimFactory.create(getSourceSection(),
              argumentNodes[0], argumentNodes[1], argumentNodes[2],
              ToArgumentsArrayNodeGen.create(null, null)));
      }
      return makeSend();
    }

    protected PreevaluatedExpression specializeQuaternary(final PrimAndFact prim, final Object[] arguments) {
      if (prim != null) {
        IntToByDoMessageNode newNode = prim.createNode(arguments, argumentNodes, getSourceSection());
        return replace(newNode);
      }
      return makeSend();
    }
  }

  @Instrumentable(factory = MessageSendNodeWrapper.class)
  private static final class UninitializedMessageSendNode
      extends AbstractUninitializedMessageSendNode {

    protected UninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final SourceSection source) {
      super(selector, arguments, source);
    }

    /**
     * For wrapper use only.
     */
    protected UninitializedMessageSendNode(final UninitializedMessageSendNode wrappedNode) {
      super(wrappedNode.selector, null, null);
    }

    @Override
    protected GenericMessageSendNode makeOrdenarySend() {
      VM.insertInstrumentationWrapper(this);
      ExpressionNode rcvr = unwrapIfNecessary(argumentNodes[0]);
      rcvr.markAsVirtualInvokeReceiver();
      GenericMessageSendNode send = new GenericMessageSendNode(selector,
          argumentNodes,
          UninitializedDispatchNode.createRcvrSend(
              getSourceSection(), selector, AccessModifier.PUBLIC),
          getSourceSection());
      replace(send);
      VM.insertInstrumentationWrapper(send);
      assert unwrapIfNecessary(argumentNodes[0]) == rcvr : "for some reason these are not the same anymore. race?";
      VM.insertInstrumentationWrapper(argumentNodes[0]);
      return send;
    }

    @Override
    protected PreevaluatedExpression makeSpecialSend() {
      VM.insertInstrumentationWrapper(this);

      ISpecialSend rcvrNode = (ISpecialSend) unwrapIfNecessary(argumentNodes[0]);
      ((ExpressionNode) rcvrNode).markAsVirtualInvokeReceiver();
      AbstractDispatchNode dispatch;

      if (rcvrNode.isSuperSend()) {
        dispatch = UninitializedDispatchNode.createSuper(
            getSourceSection(), selector, (ISuperReadNode) rcvrNode);
      } else {
        dispatch = UninitializedDispatchNode.createLexicallyBound(
            getSourceSection(), selector, rcvrNode.getEnclosingMixinId());
      }

      GenericMessageSendNode node = new GenericMessageSendNode(selector,
        argumentNodes, dispatch, getSourceSection());
      replace(node);
      VM.insertInstrumentationWrapper(node);
      VM.insertInstrumentationWrapper(argumentNodes[0]);
      return node;
    }
  }

  private static final class UninitializedSymbolSendNode
    extends AbstractUninitializedMessageSendNode {

    protected UninitializedSymbolSendNode(final SSymbol selector, final SourceSection source) {
      super(selector, new ExpressionNode[0], source);
    }

    @Override
    public boolean isSpecialSend() {
      return false;
    }

    @Override
    protected GenericMessageSendNode makeOrdenarySend() {
      // TODO: figure out what to do with reflective sends and how to instrument them.
      GenericMessageSendNode send = new GenericMessageSendNode(selector,
          argumentNodes,
          UninitializedDispatchNode.createRcvrSend(
              getSourceSection(), selector, AccessModifier.PUBLIC),
          getSourceSection());
      return replace(send);
    }

    @Override
    protected PreevaluatedExpression makeSpecialSend() {
      // should never be reached with isSuperSend() returning always false
      throw new RuntimeException("A symbol send should never be a special send.");
    }

    @Override
    protected PreevaluatedExpression specializeBinary(final PrimAndFact prim,
        final Object[] arguments) {
      switch (selector.getString()) {
        case "whileTrue:": {
          if (arguments[1] instanceof SBlock && arguments[0] instanceof SBlock) {
            SBlock argBlock = (SBlock) arguments[1];
            return replace(new WhileWithDynamicBlocksNode((SBlock) arguments[0],
                argBlock, true, getSourceSection()));
          }
          break;
        }
        case "whileFalse:":
          if (arguments[1] instanceof SBlock && arguments[0] instanceof SBlock) {
            SBlock    argBlock     = (SBlock)    arguments[1];
            return replace(new WhileWithDynamicBlocksNode(
                (SBlock) arguments[0], argBlock, false, getSourceSection()));
          }
          break; // use normal send
      }

      return super.specializeBinary(prim, arguments);
    }
  }

  @Instrumentable(factory = MessageSendNodeWrapper.class)
  public static final class GenericMessageSendNode
      extends AbstractMessageSendNode {

    private final SSymbol selector;

    @Child private AbstractDispatchNode dispatchNode;

    private GenericMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode, final SourceSection source) {
      super(arguments, source);
      this.selector = selector;
      this.dispatchNode = dispatchNode;
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == VirtualInvoke.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return dispatchNode.executeDispatch(frame, arguments);
    }

    public AbstractDispatchNode getDispatchListHead() {
      return dispatchNode;
    }

    public void adoptNewDispatchListHead(final AbstractDispatchNode newHead) {
      CompilerAsserts.neverPartOfCompilation();
      dispatchNode = insert(newHead);
    }

    public void replaceDispatchListHead(
        final GenericDispatchNode replacement) {
      CompilerAsserts.neverPartOfCompilation();
      dispatchNode.replace(replacement);
    }

    @Override
    public String toString() {
      String file = "";
      if (getSourceSection() != null) {
        file = " " + getSourceSection().getSource().getName();
        file += ":" + getSourceSection().getStartLine();
        file += ":" + getSourceSection().getStartColumn();
      }

      return "GMsgSend(" + selector.getString() + file + ")";
    }

    @Override
    public NodeCost getCost() {
      return Cost.getCost(dispatchNode);
    }
  }
}
