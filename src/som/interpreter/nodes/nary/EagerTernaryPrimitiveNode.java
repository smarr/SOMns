package som.interpreter.nodes.nary;

import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;

import som.interpreter.TruffleCompiler;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;
import som.vm.NotYetImplementedException;
import som.vmobjects.SSymbol;


public final class EagerTernaryPrimitiveNode extends EagerPrimitiveNode {

  @Child private ExpressionNode        receiver;
  @Child private ExpressionNode        argument1;
  @Child private ExpressionNode        argument2;
  @Child private TernaryExpressionNode primitive;

  public EagerTernaryPrimitiveNode(final SSymbol selector, final ExpressionNode receiver,
      final ExpressionNode argument1, final ExpressionNode argument2,
      final TernaryExpressionNode primitive) {
    super(selector);
    this.receiver = insert(receiver);
    this.argument1 = insert(argument1);
    this.argument2 = insert(argument2);
    this.primitive = insert(primitive);
  }

  @Override
  public boolean hasTag(final Class<? extends Tag> tag) {
    assert !(primitive instanceof WrapperNode) : "Eager primitives are expected to point directly to primitive nodes, and do not have wrapper nodes. I think, we wanted the wrapper nodes to be strictly around the eager wrappers.";
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
    return 3;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    Object rcvr = receiver.executeGeneric(frame);
    Object arg1 = argument1.executeGeneric(frame);
    Object arg2 = argument2.executeGeneric(frame);

    return executeEvaluated(frame, rcvr, arg1, arg2);
  }

  public Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, final Object argument1, final Object argument2) {
    try {
      return primitive.executeEvaluated(frame, receiver, argument1, argument2);
    } catch (UnsupportedSpecializationException e) {
      TruffleCompiler.transferToInterpreterAndInvalidate(
          "Eager Primitive with unsupported specialization.");
      return makeGenericSend().doPreEvaluated(frame,
          new Object[] {receiver, argument1, argument2});
    }
  }

  @Override
  public Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame, arguments[0], arguments[1], arguments[2]);
  }

  private AbstractMessageSendNode makeGenericSend() {
    GenericMessageSendNode node = MessageSendNode.createGeneric(selector,
        new ExpressionNode[] {receiver, argument1, argument2}, sourceSection);
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
      assert ((WrapperNode) newNode).getDelegateNode() == this : "Wrapping should not also do specialization or other changes, I think";
    } else {
      throw new NotYetImplementedException();
    }
  }
}
