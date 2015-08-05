package som.interpreter.nodes;

import som.compiler.ClassBuilder.ClassDefinitionId;
import som.interpreter.LexicalScope.ClassScope.ClassIdAndContextLevel;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public class ResolvingImplicitReceiverSend extends AbstractMessageSendNode {

  private final SSymbol     selector;
  private final MethodScope currentScope;
  private final ClassDefinitionId classDefId;

  public ResolvingImplicitReceiverSend(final SSymbol selector,
      final ExpressionNode[] arguments, final MethodScope currentScope,
      final ClassDefinitionId classDefId, final SourceSection source) {
    super(arguments, source);
    this.selector     = selector;
    this.currentScope = currentScope;
    this.classDefId   = classDefId;
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
    PreevaluatedExpression newNode = atomic(() -> specialize(args));
    return newNode.
        doPreEvaluated(frame, args);
  }

  protected PreevaluatedExpression specialize(final Object[] args) {
    PreevaluatedExpression newNode;
    // first check whether it is an outer send
    // it it is, we get the context level of the outer send and rewrite to one
    ClassIdAndContextLevel result = currentScope.lookupSlotOrClass(selector);
    if (result != null) {
      assert result.contextLevel >= 0;
      OuterObjectRead outer = OuterObjectReadNodeGen.create(result.contextLevel,
          classDefId, result.classId, getSourceSection(), argumentNodes[0]);
      argumentNodes[0] = outer;
      newNode = MessageSendNode.createMessageSend(selector, argumentNodes,
          getSourceSection());
      replace((ExpressionNode) newNode);
      args[0] = outer.executeEvaluated(args[0]);
    } else {
      // no outer send, so, must be a self send
      newNode = MessageSendNode.createMessageSend(selector, argumentNodes,
          getSourceSection());
      replace((ExpressionNode) newNode);
    }
    return newNode;
  }

  @Override
  public String toString() {
    return "ImplicitSend(" + selector.toString() + ")";
  }
}
