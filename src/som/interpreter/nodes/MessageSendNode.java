package som.interpreter.nodes;

import static som.interpreter.nodes.SOMNode.unwrapIfNecessary;

import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

import bd.nodes.PreevaluatedExpression;
import bd.primitives.Specializer;
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
import som.vmobjects.SSymbol;
import tools.Send;
import tools.SourceCoordinate;
import tools.dym.Tags.VirtualInvoke;


public final class MessageSendNode {

  public static ExpressionNode createMessageSend(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source, final VM vm) {
    Primitives prims = vm.getPrimitives();
    Specializer<VM, ExpressionNode, SSymbol> specializer =
        prims.getParserSpecializer(selector, arguments);
    if (specializer != null) {
      EagerlySpecializableNode newNode = (EagerlySpecializableNode) specializer.create(null,
          arguments, source, !specializer.noWrapper());
      for (ExpressionNode exp : arguments) {
        unwrapIfNecessary(exp).markAsPrimitiveArgument();
      }
      if (specializer.noWrapper()) {
        return newNode;
      } else {
        return newNode.wrapInEagerWrapper(selector, arguments, vm);
      }
    } else {
      return new UninitializedMessageSendNode(selector, arguments, vm).initialize(source);
    }
  }

  public static AbstractMessageSendNode adaptSymbol(final SSymbol newSelector,
      final AbstractMessageSendNode node, final VM vm) {
    assert node instanceof UninitializedMessageSendNode;
    return new UninitializedMessageSendNode(newSelector, node.argumentNodes, vm).initialize(
        node.sourceSection);
  }

  public static AbstractMessageSendNode createForPerformNodes(final SourceSection source,
      final SSymbol selector, final VM vm) {
    AbstractMessageSendNode result = new UninitializedSymbolSendNode(selector, vm);

    // This is currently needed for interop, don't have source section there
    if (source != null) {
      result.initialize(source);
    }
    return result;
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
      dispatch =
          UninitializedDispatchNode.createRcvrSend(source, selector, AccessModifier.PUBLIC);
    }

    GenericMessageSendNode result =
        new GenericMessageSendNode(selector, argumentNodes, dispatch);
    if (source != null) {
      result.initialize(source);
    }
    return result;
  }

  public abstract static class AbstractMessageSendNode extends ExprWithTagsNode
      implements PreevaluatedExpression, Send {

    @Children protected final ExpressionNode[] argumentNodes;

    protected AbstractMessageSendNode(final ExpressionNode[] arguments) {
      this.argumentNodes = arguments;
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == CallTag.class) {
        return true;
      }
      return super.isTaggedWith(tag);
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
    protected final VM      vm;

    protected AbstractUninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments, final VM vm) {
      super(arguments);
      this.selector = selector;
      this.vm = vm;
    }

    @Override
    public String toString() {
      return "UninitMsgSend(" + selector.toString() + ")";
    }

    @Override
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
      return specialize(arguments).doPreEvaluated(frame, arguments);
    }

    private PreevaluatedExpression specialize(final Object[] arguments) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Specialize Message Node");

      Primitives prims = vm.getPrimitives();

      Specializer<VM, ExpressionNode, SSymbol> specializer =
          prims.getEagerSpecializer(selector, arguments, argumentNodes);

      Lock lock = getLock();
      try {
        lock.lock();
        if (specializer != null) {
          boolean noWrapper = specializer.noWrapper();
          EagerlySpecializableNode newNode =
              (EagerlySpecializableNode) specializer.create(arguments, argumentNodes,
                  sourceSection, !noWrapper);
          if (noWrapper) {
            return replace(newNode);
          } else {
            return makeEagerPrim(newNode);
          }
        }
        return makeSend();
      } finally {
        lock.unlock();
      }
    }

    protected abstract PreevaluatedExpression makeSend();

    private PreevaluatedExpression makeEagerPrim(final EagerlySpecializableNode prim) {
      Lock lock = getLock();
      try {
        lock.lock();
        return makeEagerPrimUnsyced(prim);
      } finally {
        lock.unlock();
      }
    }

    private PreevaluatedExpression makeEagerPrimUnsyced(final EagerlySpecializableNode prim) {
      VM.insertInstrumentationWrapper(this);
      assert prim.getSourceSection() != null;

      PreevaluatedExpression result =
          (PreevaluatedExpression) replace(
              prim.wrapInEagerWrapper(selector, argumentNodes, vm));

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
        final ExpressionNode[] arguments, final VM vm) {
      super(selector, arguments, vm);
    }

    /**
     * For wrapper use only.
     */
    protected UninitializedMessageSendNode(final UninitializedMessageSendNode wrappedNode) {
      super(wrappedNode.selector, null, null);
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

    protected UninitializedSymbolSendNode(final SSymbol selector, final VM vm) {
      super(selector, new ExpressionNode[0], vm);
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

    private GenericMessageSendNode(final SSymbol selector, final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode) {
      super(arguments);
      this.selector = selector;
      this.dispatchNode = dispatchNode;
    }

    @Override
    public SSymbol getSelector() {
      return selector;
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
