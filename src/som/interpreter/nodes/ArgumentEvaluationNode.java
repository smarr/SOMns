package som.interpreter.nodes;

import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;


public class ArgumentEvaluationNode extends SOMNode {

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

  @ExplodeLoop
  public SObject[] doArray(final VirtualFrame frame) {
    if (arguments == null || arguments.length == 0) {
      return null;
    }

    SObject[] result = new SObject[arguments.length];

    for (int i = 0; i < arguments.length; i++) {
      result[i] = arguments[i].executeGeneric(frame);
    }

    return result;
  }

  public Object executeArray(final VirtualFrame frame) {
    return doArray(frame);
  }

  public ArgumentEvaluationNode cloneForInlining() {
    ExpressionNode[] args = new ExpressionNode[arguments.length];

    for (int i = 0; i < arguments.length; i++) {
      args[i] = arguments[i].cloneForInlining();
    }

    return new ArgumentEvaluationNode(args);
  }

}
