package som.interpreter.nodes;

import som.interpreter.nodes.messages.BinarySendNode;
import som.interpreter.nodes.messages.KeywordSendNode;
import som.interpreter.nodes.messages.TernarySendNode;
import som.interpreter.nodes.messages.UnarySendNode;
import som.vm.Universe;
import som.vmobjects.SSymbol;


public final class NodeFactory {

  private NodeFactory() { }

  public static UnaryMessageNode createUnaryMessageNode(final SSymbol selector, final Universe universe, final ExpressionNode receiver) {
    return UnarySendNode.create(selector, universe, receiver);
  }

  public static BinaryMessageNode createBinaryMessageNode(final SSymbol selector, final Universe universe, final ExpressionNode receiver, final ExpressionNode arg) {
    return BinarySendNode.create(selector, universe, receiver, arg);
  }

  public static AbstractMessageNode createMessageNode(final SSymbol selector, final Universe universe, final ExpressionNode receiver, final ExpressionNode[] arguments) {
    switch (arguments.length) {
      case 0:
        return createUnaryMessageNode(selector, universe, receiver);
      case 1:
        return BinarySendNode.create(selector, universe, receiver, arguments[0]);
      case 2:
        return TernarySendNode.create(selector, universe, receiver, arguments[0], arguments[1]);
      default:
        ArgumentEvaluationNode args = new ArgumentEvaluationNode(arguments);
        return KeywordSendNode.create(selector, universe, receiver, args);
    }
  }
}
