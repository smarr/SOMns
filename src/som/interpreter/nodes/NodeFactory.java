package som.interpreter.nodes;

import som.vm.Universe;
import som.vmobjects.SSymbol;


public class NodeFactory {
  public static MessageNode createMessageNode(final SSymbol selector,
      final Universe universe, final ExpressionNode receiver, final ArgumentEvaluationNode arguments) {
    return MessageNodeFactory.create(selector, universe, receiver, arguments);
  }
}
