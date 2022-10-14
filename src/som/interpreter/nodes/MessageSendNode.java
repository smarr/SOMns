package som.interpreter.nodes;

import static som.interpreter.nodes.SOMNode.unwrapIfNecessary;

import java.util.Map;
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Specializer;
import bd.primitives.nodes.PreevaluatedExpression;
import bd.source.SourceCoordinate;
import bd.tools.nodes.Invocation;
import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.Invokable;
import som.interpreter.SArguments;
import som.interpreter.TruffleCompiler;
import som.interpreter.actors.ReceivedMessage;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.DispatchChain.Cost;
import som.interpreter.nodes.dispatch.UninitializedDispatchNode;
import som.interpreter.nodes.nary.EagerPrimitiveNode;
import som.interpreter.nodes.nary.EagerlySpecializableNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.primitives.reflection.AbstractSymbolDispatch;
import som.vm.NotYetImplementedException;
import som.vm.Primitives;
import som.vmobjects.SSymbol;
import tools.asyncstacktraces.ShadowStackEntry;
import tools.dym.Tags.VirtualInvoke;
import tools.dym.profiles.DispatchProfile;


public final class MessageSendNode {

  public static ExpressionNode createMessageSend(final SSymbol selector,
      final ExpressionNode[] arguments, final SourceSection source, final VM vm) {
    for (ExpressionNode exp : arguments) {
      unwrapIfNecessary(exp).markAsArgument();
    }

    Primitives prims = vm.getPrimitives();
    Specializer<VM, ExpressionNode, SSymbol> specializer =
        prims.getParserSpecializer(selector, arguments);
    if (specializer != null) {
      EagerlySpecializableNode newNode = (EagerlySpecializableNode) specializer.create(null,
          arguments, source, !specializer.noWrapper(), vm);
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

  @GenerateWrapper
  public abstract static class AbstractMessageSendNode extends ExprWithTagsNode
      implements PreevaluatedExpression, Invocation<SSymbol> {

    @Children protected final ExpressionNode[] argumentNodes;

    protected AbstractMessageSendNode(final ExpressionNode[] arguments) {
      this.argumentNodes = arguments;
    }

    /** For wrappers only. */
    protected AbstractMessageSendNode() {
      this.argumentNodes = null;
    }

    /**
     * HACK, TODO: remove if possible. This is a work around for a javac or TruffleDSL bug,
     * which causes the generic parameter of {@link Invocation} to end up as ? in the generated
     * file.
     */
    @Override
    public abstract SSymbol getInvocationIdentifier();

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == CallTag.class) {
        return true;
      }
      return super.hasTag(tag);
    }

    /** Introduced for the TruffleDSL to generate the method on the wrapper. */
    public abstract Object executeEvaluated(VirtualFrame frame, Object[] arguments);

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = evaluateArguments(frame);
      return doPreEvaluated(frame, arguments);
    }

    @ExplodeLoop
    private Object[] evaluateArguments(final VirtualFrame frame) {
      Object[] arguments = SArguments.allocateArgumentsArray(argumentNodes);
      for (int i = 0; i < argumentNodes.length; i++) {
        arguments[i] = argumentNodes[i].executeGeneric(frame);
        assert arguments[i] != null : "Some expression evaluated to null, which is not supported.";
        assert !(arguments[i] instanceof ShadowStackEntry);
      }
      // We allocate room for the arguments, but it is not set if non
      // SArguments.setShadowStackEntryWithCache(arguments, this, shadowStackEntryLoad, frame,
      // false);
      return arguments;
    }

    @Override
    public WrapperNode createWrapper(final ProbeNode probe) {
      Node parent = getParent();
      // this.isSafelyReplaceableBy(newNode)
      if (parent instanceof ReceivedMessage) {
        return new AbstractMessageSendNodeWrapper(this, probe);
      } else if (parent instanceof Invokable || parent instanceof SequenceNode
          || parent instanceof ExprWithTagsNode
          || parent instanceof ExceptionSignalingNode
          || parent instanceof EagerPrimitiveNode) {
        return new ExpressionNodeWrapper(this, probe);
      }

      if (parent.getClass().getSuperclass() == Node.class) {
        parent = parent.getParent();
        if (parent instanceof AbstractSymbolDispatch) {
          return new AbstractMessageSendNodeWrapper(this, probe);
        }
      }

      throw new NotYetImplementedException();
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
    public SSymbol getInvocationIdentifier() {
      return selector;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      // This is a branch never taken, none of the code here should be compiled.
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return super.executeGeneric(frame);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object[] arguments) {
      return doPreEvaluated(frame, arguments);
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
                  sourceSection, !noWrapper, vm);
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
      assert prim.getSourceSection() != null;

      PreevaluatedExpression result =
          (PreevaluatedExpression) replace(
              prim.wrapInEagerWrapper(selector, argumentNodes, vm));

      notifyInserted((Node) result);
      return result;
    }
  }

  protected static class UninitializedMessageSendNode
      extends AbstractUninitializedMessageSendNode {

    protected UninitializedMessageSendNode(final SSymbol selector,
        final ExpressionNode[] arguments, final VM vm) {
      super(selector, arguments, vm);
    }

    /**
     * For wrapper use only.
     */
    protected UninitializedMessageSendNode() {
      super(null, null, null);
    }

    @Override
    protected GenericMessageSendNode makeSend() {
      GenericMessageSendNode send = createGeneric(selector, argumentNodes, sourceSection);
      replace(send);
      notifyInserted(send);
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

  public static final class GenericMessageSendNode extends AbstractMessageSendNode
      implements DispatchProfile {

    private final SSymbol selector;

    @Child private AbstractDispatchNode dispatchNode;

    private GenericMessageSendNode(final SSymbol selector, final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode) {
      super(arguments);
      this.selector = selector;
      this.dispatchNode = dispatchNode;
    }

    /** For wrappers. */
    protected GenericMessageSendNode() {
      this(null, null, null);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return selector;
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == VirtualInvoke.class) {
        return true;
      } else {
        return super.hasTag(tag);
      }
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object[] arguments) {
      return doPreEvaluated(frame, arguments);
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame, final Object[] arguments) {
      return dispatchNode.executeDispatch(frame, arguments);
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

    @Override
    public void collectDispatchStatistics(final Map<Invokable, Integer> result) {
      dispatchNode.collectDispatchStatistics(result);
    }
  }
}
