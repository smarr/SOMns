package som.interpreter.nodes;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.instrumentation.MessageSendNodeWrapper;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.LexicalScope.MixinScope.MixinIdAndContextLevel;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;


@Instrumentable(factory = MessageSendNodeWrapper.class)
public final class ResolvingImplicitReceiverSend extends AbstractMessageSendNode {

  private final SSymbol     selector;
  private final MethodScope currentScope;
  private final MixinDefinitionId mixinId;

  // this is only a helper field, used to handle the specialization race
  private PreevaluatedExpression replacedBy;
  private OuterObjectRead        newReceiverNode;

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
    CompilerDirectives.transferToInterpreter();
    return super.executeGeneric(frame);
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
    CompilerDirectives.transferToInterpreter();
    // this specialize method is designed to be execute only once and
    // tracks its replacement nodes to avoid re-specialization in case of
    // re-execution
    PreevaluatedExpression newNode = atomic(() -> specialize(args));
    return newNode.
        doPreEvaluated(frame, args);
  }

  protected PreevaluatedExpression specialize(final Object[] args) {
    // first check whether it is an outer send
    // it it is, we get the context level of the outer send and rewrite to one
    MixinIdAndContextLevel result = currentScope.lookupSlotOrClass(selector);
    if (result != null) {
      if (replacedBy == null) {
        assert result.contextLevel >= 0;

        newReceiverNode = OuterObjectReadNodeGen.create(result.contextLevel,
            mixinId, result.mixinId, sourceSection, argumentNodes[0]);
        ExpressionNode[] msgArgNodes = argumentNodes.clone();
        msgArgNodes[0] = newReceiverNode;

        replacedBy = MessageSendNode.createMessageSend(selector, msgArgNodes,
            getSourceSection());

        replace((ExpressionNode) replacedBy);
      }
      args[0] = newReceiverNode.executeEvaluated(args[0]);
    } else {
      if (replacedBy == null) {
        replacedBy = MessageSendNode.createMessageSend(selector, argumentNodes,
            getSourceSection());
        replace((ExpressionNode) replacedBy);
      }
    }
    return replacedBy;
  }

  @Override
  public String toString() {
    return "ImplicitSend(" + selector.toString() + ")";
  }
}
