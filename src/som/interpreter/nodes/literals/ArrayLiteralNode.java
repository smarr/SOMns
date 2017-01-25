package som.interpreter.nodes.literals;


import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SMutableArray;

public final class ArrayLiteralNode extends LiteralNode {
  @Children private final ExpressionNode[] expressions;

  public ArrayLiteralNode(final ExpressionNode[] expressions, final SourceSection source) {
    super(source);
    assert source != null;
    this.expressions = expressions;
  }

  @ExplodeLoop
  private SMutableArray evaluatedExpressions(final VirtualFrame frame) {
    Object[] arr = new Object[expressions.length];
    for (int i = 0; i < expressions.length; i++) {
      arr[i] = expressions[i].executeGeneric(frame);
    }

    return new SMutableArray(arr, Classes.arrayClass);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return evaluatedExpressions(frame);
  }
}
