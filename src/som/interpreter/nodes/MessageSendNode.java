package som.interpreter.nodes;

import static som.interpreter.nodes.SOMNode.unwrapIfNecessary;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
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
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.nodes.nary.EagerlySpecializableNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vm.Primitives;
import som.vm.Primitives.Specializer;
import som.vmobjects.SSymbol;
import tools.SourceCoordinate;
import tools.dym.Tags.VirtualInvoke;


public final class MessageSendNode {

  public static ExpressionNode createMessageSend(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source, final VM vm) {
    Primitives prims = vm.getPrimitives();
    Specializer<EagerlySpecializableNode> specializer =
        prims.getParserSpecializer(selector, arguments);
    if (specializer != null) {
      EagerlySpecializableNode newNode = specializer.create(null, arguments, source, !specializer.noWrapper());
      for (ExpressionNode exp : arguments) {
        unwrapIfNecessary(exp).markAsPrimitiveArgument();
      }
      if (specializer.noWrapper()) {
        return newNode;
      } else {
        return newNode.wrapInEagerWrapper(newNode, selector, arguments);
      }
    } else {
      return new UninitializedMessageSendNode(selector, arguments, source, vm);
    }
  }

  public static AbstractMessageSendNode adaptSymbol(final SSymbol newSelector,
      final AbstractMessageSendNode node, final VM vm) {
    assert node instanceof UninitializedMessageSendNode;
    return new UninitializedMessageSendNode(newSelector, node.argumentNodes,
        node.getSourceSection(), vm);
  }

  public static AbstractMessageSendNode createForPerformNodes(
      final SSymbol selector, final SourceSection source, final VM vm) {
    return new UninitializedSymbolSendNode(selector, source, vm);
  }

  public static GenericMessageSendNode createGeneric(final SSymbol selector,
      final ExpressionNode[] argumentNodes, final SourceSection source) {
    AbstractDispatchNode dispatch = null;

    if (argumentNodes != null && argumentNodes.length > 0) {
      ExpressionNode rcvrNode = unwrapIfNecessary(argumentNodes[0]);
      rcvrNode.markAsVirtualInvokeReceiver();

      if (unwrapIfNecessary(argumentNodes[0]) instanceof ISpecialSend) {
        if (((ISpecialSend) rcvrNode).isSuperSend()) {
          dispatch = UninitializedDispatchNode.createSuper(
              source, selector, (ISuperReadNode) rcvrNode);
        } else {
          dispatch = UninitializedDispatchNode.createLexicallyBound(
              source, selector, ((ISpecialSend) rcvrNode).getEnclosingMixinId());
        }
      }
    }

    if (dispatch == null) {
      dispatch = UninitializedDispatchNode.createRcvrSend(source, selector, AccessModifier.PUBLIC);
    }

    return new GenericMessageSendNode(selector, argumentNodes, dispatch, source);
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
      if (tag == CallTag.class) {
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
        assert arguments[i] != null : "Some expression evaluated to null, which is not supported.";
      }
      return arguments;
    }
  }

  public abstract static class AbstractUninitializedMessageSendNode
      extends AbstractMessageSendNode {

    protected final SSymbol selector;
    protected final VM vm;

    protected AbstractUninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final SourceSection source, final VM vm) {
      super(arguments, source);
      this.selector = selector;
      this.vm       = vm;
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

      Primitives prims = vm.getPrimitives();

      Specializer<EagerlySpecializableNode> specializer = prims.getEagerSpecializer(selector,
          arguments, argumentNodes);

      synchronized (getLock()) {
        if (specializer != null) {
          EagerlySpecializableNode newNode = specializer.create(arguments, argumentNodes, getSourceSection(), !specializer.noWrapper());
          if (specializer.noWrapper()) {
            return replace(newNode);
          } else {
            return makeEagerPrim(newNode);
          }
        }
        return makeSend();
      }
    }

    protected abstract PreevaluatedExpression makeSend();

    private PreevaluatedExpression makeEagerPrim(final EagerlySpecializableNode prim) {
      synchronized (getLock()) {
        return makeEagerPrimUnsyced(prim);
      }
    }

    private PreevaluatedExpression makeEagerPrimUnsyced(final EagerlySpecializableNode prim) {
      VM.insertInstrumentationWrapper(this);
      assert prim.getSourceSection() != null;

      PreevaluatedExpression result = replace(prim.wrapInEagerWrapper(prim, selector, argumentNodes));

      VM.insertInstrumentationWrapper((Node) result);

      for (ExpressionNode exp : argumentNodes) {
        unwrapIfNecessary(exp).markAsPrimitiveArgument();
        VM.insertInstrumentationWrapper(exp);
      }

      return result;
    }
  }

  @Instrumentable(factory = MessageSendNodeWrapper.class)
  private static final class UninitializedMessageSendNode
      extends AbstractUninitializedMessageSendNode {

    protected UninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments,
        final SourceSection source, final VM vm) {
      super(selector, arguments, source, vm);
    }

    /**
     * For wrapper use only.
     */
    protected UninitializedMessageSendNode(final UninitializedMessageSendNode wrappedNode) {
      super(wrappedNode.selector, null, null, null);
    }

    @Override
    protected GenericMessageSendNode makeSend() {
      VM.insertInstrumentationWrapper(this);
      GenericMessageSendNode send = createGeneric(selector, argumentNodes, sourceSection);
      replace(send);
      VM.insertInstrumentationWrapper(send);
      VM.insertInstrumentationWrapper(argumentNodes[0]);
      return send;
    }
  }

  private static final class UninitializedSymbolSendNode
    extends AbstractUninitializedMessageSendNode {

    protected UninitializedSymbolSendNode(final SSymbol selector,
        final SourceSection source, final VM vm) {
      super(selector, new ExpressionNode[0], source, vm);
    }

    @Override
    public boolean isSpecialSend() {
      return false;
    }

    @Override
    protected GenericMessageSendNode makeSend() {
      // TODO: figure out what to do with reflective sends and how to instrument them.
      GenericMessageSendNode send = createGeneric(selector, argumentNodes, sourceSection);
      return replace(send);
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
      return dispatchNode.executeDispatch(arguments);
    }

    public AbstractDispatchNode getDispatchListHead() {
      return dispatchNode;
    }

    public void adoptNewDispatchListHead(final AbstractDispatchNode newHead) {
      CompilerAsserts.neverPartOfCompilation();
      dispatchNode = insert(newHead);
    }

    public void replaceDispatchListHead(final AbstractDispatchNode replacement) {
      CompilerAsserts.neverPartOfCompilation();
      dispatchNode.replace(replacement);
    }

    @Override
    public String toString() {
      String file = "";
      if (sourceSection != null) {
        file = " " + sourceSection.getSource().getName() +
            SourceCoordinate.getLocationQualifier(sourceSection);
      }

      return "GMsgSend(" + selector.getString() + file + ")";
    }

    @Override
    public NodeCost getCost() {
      return Cost.getCost(dispatchNode);
    }
  }
}
