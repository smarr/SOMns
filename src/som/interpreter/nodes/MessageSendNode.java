package som.interpreter.nodes;

import static som.interpreter.nodes.SOMNode.unwrapIfNecessary;

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
import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.Invokable;
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
import tools.Send;
import tools.SourceCoordinate;
import tools.dym.Tags.VirtualInvoke;


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

    GenericMessageSendNode result;
    int arity = argumentNodes == null ? 0 : argumentNodes.length;
    switch (arity) {
      case 1:
        result = new UnaryMessageSendNode(selector, argumentNodes, dispatch);
        break;
      case 2:
        result = new BinaryMessageSendNode(selector, argumentNodes, dispatch);
        break;
      case 3:
        result = new TernaryMessageSendNode(selector, argumentNodes, dispatch);
        break;
      case 4:
        result = new QuaternaryMessageSendNode(selector, argumentNodes, dispatch);
        break;
      default:
        result = new KeywordMessageSendNode(selector, argumentNodes, dispatch);
        break;
    }

    if (source != null) {
      result.initialize(source);
    }
    return result;
  }

  @GenerateWrapper
  public abstract static class AbstractMessageSendNode extends ExprWithTagsNode
      implements PreevaluatedExpression, Send {

    @Children protected final ExpressionNode[] argumentNodes;

    protected AbstractMessageSendNode(final ExpressionNode[] arguments) {
      this.argumentNodes = arguments;
    }

    /** For wrappers only. */
    protected AbstractMessageSendNode() {
      this.argumentNodes = null;
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == CallTag.class) {
        return true;
      }
      return super.hasTag(tag);
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

  public abstract static class GenericMessageSendNode extends AbstractMessageSendNode {
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
    public SSymbol getSelector() {
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

    public AbstractDispatchNode getDispatchListHead() {
      return dispatchNode;
    }

    @Override
    public final Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return dispatchNode.executeDispatch(frame, arguments);
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

  public static final class KeywordMessageSendNode extends GenericMessageSendNode {

    private KeywordMessageSendNode(final SSymbol selector, final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode) {
      super(selector, arguments, dispatchNode);
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

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = evaluateArguments(frame);
      return doPreEvaluated(frame, arguments);
    }
  }

  public static final class UnaryMessageSendNode extends GenericMessageSendNode {

    private UnaryMessageSendNode(final SSymbol selector, final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode) {
      super(selector, arguments, dispatchNode);
      assert arguments.length == 1;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = new Object[] {argumentNodes[0].executeGeneric(frame)};
      return doPreEvaluated(frame, arguments);
    }
  }

  public static final class BinaryMessageSendNode extends GenericMessageSendNode {

    private BinaryMessageSendNode(final SSymbol selector, final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode) {
      super(selector, arguments, dispatchNode);
      assert arguments.length == 2;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = new Object[] {
          argumentNodes[0].executeGeneric(frame),
          argumentNodes[1].executeGeneric(frame)};
      return doPreEvaluated(frame, arguments);
    }
  }

  public static final class TernaryMessageSendNode extends GenericMessageSendNode {

    private TernaryMessageSendNode(final SSymbol selector, final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode) {
      super(selector, arguments, dispatchNode);
      assert arguments.length == 3;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = new Object[] {
          argumentNodes[0].executeGeneric(frame),
          argumentNodes[1].executeGeneric(frame),
          argumentNodes[2].executeGeneric(frame)};
      return doPreEvaluated(frame, arguments);
    }
  }

  public static final class QuaternaryMessageSendNode extends GenericMessageSendNode {

    private QuaternaryMessageSendNode(final SSymbol selector, final ExpressionNode[] arguments,
        final AbstractDispatchNode dispatchNode) {
      super(selector, arguments, dispatchNode);
      assert arguments.length == 4;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object[] arguments = new Object[] {
          argumentNodes[0].executeGeneric(frame),
          argumentNodes[1].executeGeneric(frame),
          argumentNodes[2].executeGeneric(frame),
          argumentNodes[3].executeGeneric(frame)};
      return doPreEvaluated(frame, arguments);
    }
  }
}
