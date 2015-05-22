package som.interpreter.nodes;

import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


public class ResolvingImplicitReceiverSend extends AbstractMessageSendNode {

  private final SSymbol     selector;
  private final MethodScope currentScope;

  public ResolvingImplicitReceiverSend(final SSymbol selector,
      final ExpressionNode[] arguments, final MethodScope currentScope,
      final SourceSection source) {
    super(arguments, source);
    this.selector     = selector;
    this.currentScope = currentScope;
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
    // TODO: now we got all the lexical information, and need to resolve the
    //       receiver of this send
    PreevaluatedExpression newNode;

    // first check whether it is an outer send
    // it it is, we get the context level of the outer send and rewrite to one
    int contextLevel = currentScope.lookupContextLevelOfSlotOrClass(selector);
    if (contextLevel != -1) {
      assert contextLevel >= 0;
      OuterObjectRead outer = OuterObjectReadNodeGen.create(contextLevel,
          getSourceSection(), argumentNodes[0]);
      argumentNodes[0] = outer;
      newNode = MessageSendNode.createOuterSend(selector, argumentNodes, getSourceSection());
      replace((ExpressionNode) newNode);
      args[0] = outer.executeEvaluated(args[0]);
    } else {
      // no outer send, so, must be a self send
      newNode = MessageSendNode.createSelfSend(selector, argumentNodes, getSourceSection());
      replace((ExpressionNode) newNode);
    }

    return newNode.doPreEvaluated(frame, args);
  }

}
