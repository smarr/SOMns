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

  // this is only a helper field, used to handle the specialization race
  private PreevaluatedExpression replacedBy;

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
    // the specialize() is racy and needs to be handled with care.
    // it rewrites existing nodes and adapts the args array
    // in some cases
    //
    // specifically, it takes the argumentNode[0] node and move it one level
    // down, under another Outer node
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
      if (replacedBy == null) {
        assert result.contextLevel >= 0;
        OuterObjectRead outer = OuterObjectReadNodeGen.create(result.contextLevel,
            classDefId, result.classId, getSourceSection(), argumentNodes[0]);
        argumentNodes[0] = insert(outer);
        newNode = MessageSendNode.createMessageSend(selector, argumentNodes,
            getSourceSection());
        replace((ExpressionNode) newNode);
        args[0] = outer.executeEvaluated(args[0]);
        replacedBy = newNode;
      } else {
        // this can happen as part of a race condition
        // and it means argumentNodes[0] has already been replaced
        // and we can use it to determine the outer receiver, but don't
        // need to do much else
        args[0] = ((OuterObjectRead) argumentNodes[0]).executeEvaluated(args[0]);
        newNode = replacedBy;
      }
    } else {
      if (replacedBy == null) {
        newNode = MessageSendNode.createMessageSend(selector, argumentNodes,
            getSourceSection());
        replace((ExpressionNode) newNode);
      } else {
        newNode = replacedBy;
      }
    }
    return newNode;
  }

  @Override
  public String toString() {
    return "ImplicitSend(" + selector.toString() + ")";
  }
}
