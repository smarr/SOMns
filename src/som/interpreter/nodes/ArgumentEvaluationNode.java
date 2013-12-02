package som.interpreter.nodes;

import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;


public class ArgumentEvaluationNode extends ExpressionNode {

  @Children private final ExpressionNode[] arguments;

  public ArgumentEvaluationNode(final ExpressionNode[] arguments) {
    this.arguments = adoptChildren(arguments);
  }

  public ArgumentEvaluationNode() {
    this.arguments = null;
  }

  public ExpressionNode getArgument(final int idx) {
    return arguments[idx];
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return executeArray(frame);
  }

  @Override
  @ExplodeLoop
  public Object[] executeArray(final VirtualFrame frame) {
    if (arguments == null || arguments.length == 0) {
      return null;
    }

    SAbstractObject[] result = new SAbstractObject[arguments.length];

    for (int i = 0; i < arguments.length; i++) {
      result[i] = (SAbstractObject) arguments[i].executeGeneric(frame); // TODO: Work out whether there is another way than this cast!
    }

    return result;
  }

  @Override
  public ArgumentEvaluationNode cloneForInlining() {
    if (arguments == null) {
      return this;
    }

    ExpressionNode[] args = new ExpressionNode[arguments.length];

    for (int i = 0; i < arguments.length; i++) {
      args[i] = arguments[i].cloneForInlining();
    }

    return new ArgumentEvaluationNode(args);
  }
}
