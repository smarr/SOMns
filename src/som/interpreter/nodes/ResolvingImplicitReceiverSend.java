package som.interpreter.nodes;

import java.util.List;
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;

import bd.primitives.nodes.PreevaluatedExpression;
import som.VM;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.instrumentation.MessageSendNodeWrapper;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;


@Instrumentable(factory = MessageSendNodeWrapper.class)
public final class ResolvingImplicitReceiverSend extends AbstractMessageSendNode {

  private final SSymbol           selector;
  private final MethodScope       currentScope;
  private final MixinDefinitionId mixinId;
  private final VM                vm;

  /**
   * A helper field used to make sure we specialize this node only once,
   * because it gets removed, and races on the removal are very problematic.
   */
  private PreevaluatedExpression replacedBy;

  /**
   * In case this node becomes an outer send, we need to recalculate the
   * receiver also when the specialization was racy.
   */
  private OuterObjectRead newReceiverNodeForOuterSend;

  public ResolvingImplicitReceiverSend(final SSymbol selector,
      final ExpressionNode[] arguments, final MethodScope currentScope,
      final MixinDefinitionId mixinId, final VM vm) {
    super(arguments);
    this.selector = selector;
    this.currentScope = currentScope;
    this.mixinId = mixinId;
    this.vm = vm;
  }

  /**
   * For wrapped nodes only.
   */
  protected ResolvingImplicitReceiverSend(final ResolvingImplicitReceiverSend wrappedNode) {
    super(null);
    this.selector = wrappedNode.selector;
    this.currentScope = wrappedNode.currentScope;
    this.mixinId = wrappedNode.mixinId;
    this.vm = wrappedNode.vm;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    return super.executeGeneric(frame);
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
    CompilerDirectives.transferToInterpreterAndInvalidate();
    // this specialize method is designed to be execute only once and
    // tracks its replacement nodes to avoid re-specialization in case of
    // re-execution
    PreevaluatedExpression newNode;
    Lock lock = getLock();
    try {
      lock.lock();
      newNode = specialize(frame, args);
    } finally {
      lock.unlock();
    }
    return newNode.doPreEvaluated(frame, args);
  }

  private PreevaluatedExpression specialize(final VirtualFrame frame, final Object[] args) {
    if (replacedBy != null) {
      // already has been specialized
      if (newReceiverNodeForOuterSend != null) {
        // need to recalculate the real receiver for outer sends
        args[0] = newReceiverNodeForOuterSend.executeGeneric(frame);
      }
      return replacedBy;
    }
    // first check whether it is an outer send
    // it it is, we get the context level of the outer send and rewrite to one
    List<MixinDefinitionId> result = currentScope.lookupSlotOrClass(selector);
    if (result != null && result.size() > 1) {
      assert mixinId == result.get(0);
      result.remove(0);

      ExpressionNode[] msgArgNodes = argumentNodes.clone();
      MixinDefinitionId currentMixin = mixinId;

      for (MixinDefinitionId enclosingMixin : result) {
        newReceiverNodeForOuterSend =
            OuterObjectReadNodeGen.create(currentMixin, enclosingMixin, msgArgNodes[0])
                                  .initialize(sourceSection);

        msgArgNodes[0] = newReceiverNodeForOuterSend;
        args[0] = newReceiverNodeForOuterSend.executeEvaluated(args[0]);
        currentMixin = enclosingMixin;
      }

      replacedBy =
          (PreevaluatedExpression) MessageSendNode.createMessageSend(selector, msgArgNodes,
              getSourceSection(), vm);

      replace((ExpressionNode) replacedBy);
    } else {
      replacedBy =
          (PreevaluatedExpression) MessageSendNode.createMessageSend(selector, argumentNodes,
              getSourceSection(), vm);
      replace((ExpressionNode) replacedBy);
    }
    return replacedBy;
  }

  @Override
  public SSymbol getSelector() {
    return selector;
  }

  @Override
  public String toString() {
    return "ImplicitSend(" + selector.toString() + ")";
  }
}
