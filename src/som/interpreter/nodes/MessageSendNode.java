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
import som.compiler.AccessModifier;
import som.instrumentation.MessageSendNodeWrapper;
import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.dispatch.GenericDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.nodes.nary.EagerlySpecializableNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.nodes.specialized.whileloops.WhileWithDynamicBlocksNode;
import som.vm.NotYetImplementedException;
import som.vm.Primitives;
import som.vm.Primitives.Specializer;
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

      Specializer<EagerlySpecializableNode> specializer = prims.getEagerSpecializer(selector,
          arguments, argumentNodes);

      if (specializer != null) {
        EagerlySpecializableNode newNode = specializer.create(arguments, argumentNodes, getSourceSection(), !specializer.noWrapper());
        if (specializer.noWrapper()) {
          return replace(newNode);
        } else {
          return makeEagerPrim(newNode);
        }
      }

      // let's organize the specializations by number of arguments
      // perhaps not the best, but simple
      switch (argumentNodes.length) {
        case  2: return specializeBinary(arguments);
        case  3: return specializeTernary(arguments);
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

    private PreevaluatedExpression makeEagerPrim(final EagerlySpecializableNode prim) {
      synchronized (getAtomicLock()) {
        return makeEagerPrimUnsyced(prim);
      }
    }

    private PreevaluatedExpression makeEagerPrimUnsyced(final EagerlySpecializableNode prim) {
      VM.insertInstrumentationWrapper(this);
      assert prim.getSourceSection() != null;

      for (ExpressionNode exp : argumentNodes) {
        unwrapIfNecessary(exp).markAsPrimitiveArgument();
      }

      PreevaluatedExpression result = replace(prim.wrapInEagerWrapper(prim, selector, argumentNodes));

      VM.insertInstrumentationWrapper((Node) result);

      for (ExpressionNode exp : argumentNodes) {
        VM.insertInstrumentationWrapper(exp);
      }

      return result;
    }

    protected PreevaluatedExpression specializeBinary(final Object[] arguments) {
      switch (selector.getString()) {
      }
      return makeSend();
    }

    protected PreevaluatedExpression specializeTernary(final Object[] arguments) {
      switch (selector.getString()) {
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
    protected PreevaluatedExpression specializeBinary(final Object[] arguments) {
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

      return super.specializeBinary(arguments);
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
