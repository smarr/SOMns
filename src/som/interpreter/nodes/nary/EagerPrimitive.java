package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.OperationNode;
import som.interpreter.nodes.PreevaluatedExpression;


public abstract class EagerPrimitive extends ExpressionNode
    implements OperationNode, PreevaluatedExpression {
  protected abstract void setTags(byte tagMark);
}
