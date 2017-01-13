package som.interpreter.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.instrumentation.MessageSendNodeWrapper;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.LexicalScope.MixinScope.MixinIdAndContextLevel;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;


@Instrumentable(factory = MessageSendNodeWrapper.class)
public final class ResolvingImplicitReceiverSend extends AbstractMessageSendNode {

  private final SSymbol     selector;
  private final MethodScope currentScope;
  private final MixinDefinitionId mixinId;

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
      final MixinDefinitionId mixinId, final SourceSection source) {
    super(arguments, source);
    this.selector     = selector;
    this.currentScope = currentScope;
    this.mixinId      = mixinId;
  }

  /**
   * For wrapped nodes only.
   */
  protected ResolvingImplicitReceiverSend(final ResolvingImplicitReceiverSend wrappedNode) {
    super(null, null);
    this.selector     = wrappedNode.selector;
    this.currentScope = wrappedNode.currentScope;
    this.mixinId      = wrappedNode.mixinId;
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
    synchronized (getLock()) {
       newNode = specialize(args);
    }
    return newNode.
        doPreEvaluated(frame, args);
  }

  private PreevaluatedExpression specialize(final Object[] args) {
    if (replacedBy != null) {
      // already has been specialized
      if (newReceiverNodeForOuterSend != null) {
        // need to recalculate the real receiver for outer sends
        args[0] = newReceiverNodeForOuterSend.executeEvaluated(args[0]);
      }
      return replacedBy;
    }
    // first check whether it is an outer send
    // it it is, we get the context level of the outer send and rewrite to one
    MixinIdAndContextLevel result = currentScope.lookupSlotOrClass(selector);
    if (result != null && result.contextLevel > 0) {
      newReceiverNodeForOuterSend = OuterObjectReadNodeGen.create(
          result.contextLevel, mixinId, result.mixinId, sourceSection,
          argumentNodes[0]);
      ExpressionNode[] msgArgNodes = argumentNodes.clone();
      msgArgNodes[0] = newReceiverNodeForOuterSend;

      replacedBy = (PreevaluatedExpression) MessageSendNode.createMessageSend(selector, msgArgNodes,
          getSourceSection());

      replace((ExpressionNode) replacedBy);
      args[0] = newReceiverNodeForOuterSend.executeEvaluated(args[0]);
    } else {
      replacedBy = (PreevaluatedExpression) MessageSendNode.createMessageSend(selector, argumentNodes,
          getSourceSection());
      replace((ExpressionNode) replacedBy);
    }
    return replacedBy;
  }

  @Override
  public String toString() {
    return "ImplicitSend(" + selector.toString() + ")";
  }
}
