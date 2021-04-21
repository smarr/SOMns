package som.interpreter.nodes.nary;

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;

import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.vm.NotYetImplementedException;
import som.vmobjects.SSymbol;


public final class EagerBinaryPrimitiveNode extends EagerPrimitiveNode {

  @Child private ExpressionNode       receiver;
  @Child private ExpressionNode       argument;
  @Child private BinaryExpressionNode primitive;

  public EagerBinaryPrimitiveNode(final SSymbol selector, final ExpressionNode receiver,
      final ExpressionNode argument, final BinaryExpressionNode primitive) {
    super(selector);
    this.receiver = insert(receiver);
    this.argument = insert(argument);
    this.primitive = insert(primitive);
  }

  @Override
  public EagerlySpecializableNode getPrimitiveNode() {
    return primitive;
  }

  @Override
  public boolean hasTag(final Class<? extends Tag> tag) {
    assert !(primitive instanceof WrapperNode)
        : "primitive can't be WrapperNodes to avoid double wrapping. It is: "
            + primitive.getClass().getSimpleName() + " and contains a "
            + ((WrapperNode) primitive).getDelegateNode().getClass().getSimpleName();
    return primitive.hasTagIgnoringEagerness(tag);
  }

  @Override
  public void markAsRootExpression() {
    primitive.markAsRootExpression();
  }

  @Override
  public boolean isMarkedAsRootExpression() {
    return primitive.isMarkedAsRootExpression();
  }

  @Override
  public void markAsLoopBody() {
    primitive.markAsLoopBody();
  }

  @Override
  public void markAsControlFlowCondition() {
    primitive.markAsControlFlowCondition();
  }

  @Override
  public void markAsArgument() {
    primitive.markAsArgument();
  }

  @Override
  public void markAsVirtualInvokeReceiver() {
    primitive.markAsVirtualInvokeReceiver();
  }

  @Override
  public void markAsStatement() {
    primitive.markAsStatement();
  }

  @Override
  public String getOperation() {
    return selector.getString();
  }

  @Override
  public int getNumArguments() {
    return 2;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    Object rcvr = receiver.executeGeneric(frame);
    Object arg = argument.executeGeneric(frame);

    return executeEvaluated(frame, rcvr, arg);
  }

  public Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, final Object argument) {
    try {
      return primitive.executeEvaluated(frame, receiver, argument);
    } catch (UnsupportedSpecializationException e) {
      TruffleCompiler.transferToInterpreterAndInvalidate(
          "Eager Primitive with unsupported specialization.");
      return makeGenericSend().doPreEvaluated(frame,
          new Object[] {receiver, argument});
    }
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1]);
  }

  private GenericMessageSendNode makeGenericSend() {
    GenericMessageSendNode node = MessageSendNode.createGeneric(selector,
        new ExpressionNode[] {receiver, argument}, getSourceSection());
    replace(node);
    notifyInserted(node);
    return node;
  }

  @Override
  public void setTags(final byte tagMark) {
    primitive.tagMark = tagMark;
  }

  @Override
  protected void onReplace(final Node newNode, final CharSequence reason) {
    if (newNode instanceof ExprWithTagsNode) {
      ((ExprWithTagsNode) newNode).tagMark = primitive.tagMark;
    } else if (newNode instanceof WrapperNode) {
      assert ((WrapperNode) newNode).getDelegateNode() == this
          : "Wrapping should not also do specialization or other changes, I think";
    } else {
      throw new NotYetImplementedException();
    }
  }
}
