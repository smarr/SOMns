package som.interpreter.nodes.nary;

import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;


public class EagerUnaryPrimitiveNode extends UnaryExpressionNode {

  @Child private ExpressionNode receiver;
  @Child private UnaryExpressionNode primitive;

  private final SSymbol selector;

  public EagerUnaryPrimitiveNode(final SSymbol selector,
      final ExpressionNode receiver, final UnaryExpressionNode primitive) {
    super(null);
    this.receiver  = receiver;
    this.primitive = primitive;
    this.selector = selector;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    Object rcvr = receiver.executeGeneric(frame);

    return executeEvaluated(frame, rcvr);
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    Object rcvr = receiver.executeGeneric(frame);
    executeEvaluatedVoid(frame, rcvr);
  }

  @Override
  public Object executeEvaluated(final VirtualFrame frame,
      final Object receiver) {
    try {
      return primitive.executeEvaluated(frame, receiver);
    } catch (UnsupportedSpecializationException e) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Eager Primitive with unsupported specialization.");
      return makeGenericSend().doPreEvaluated(frame, new Object[] {receiver});
    }
  }

  @Override
  public void executeEvaluatedVoid(final VirtualFrame frame,
      final Object receiver) {
    try {
      primitive.executeEvaluatedVoid(frame, receiver);
    } catch (UnsupportedSpecializationException e) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Eager Primitive with unsupported specialization.");
      makeGenericSend().doPreEvaluated(frame, new Object[] {receiver});
    }
  }

  private GenericMessageSendNode makeGenericSend() {
    GenericMessageSendNode node = GenericMessageSendNode.create(selector,
        new ExpressionNode[] {receiver}, getSourceSection());
    return replace(node);
  }
}
