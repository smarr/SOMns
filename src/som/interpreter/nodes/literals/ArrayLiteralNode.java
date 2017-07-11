package som.interpreter.nodes.literals;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SMutableArray;

public class ArrayLiteralNode extends LiteralNode {
  @Children final ExpressionNode[] expressions;

  public ArrayLiteralNode(final ExpressionNode[] expressions, final SourceSection source) {
    super(source);
    assert source != null;
    this.expressions = expressions;
  }

  @ExplodeLoop
  private Object[] evaluateAsArray(final VirtualFrame frame) {
    Object[] evaluated = new Object [expressions.length];
    for (int i = 0; i < expressions.length; i++) {
      evaluated[i] = expressions[i].executeGeneric(frame);
    }
    return evaluated;
  }

  @ExplodeLoop
  private boolean headSameTypeAsTail(final Object[] evaluated) {
    for (int i = 1; i < evaluated.length; i++) {
      if (!(evaluated[i].getClass().equals(evaluated[0].getClass()))) {
        return false;
      }
    }
    return true;
  }

  @ExplodeLoop
  private long[] convertToLongArray(final Object[] evaluated) {
    long[] converted = new long[evaluated.length];
    for (int i = 0; i < evaluated.length; i++) { converted[i] = (long) evaluated[i]; }
    return converted;
  }

  @ExplodeLoop
  private double[] convertToDoubleArray(final Object[] evaluated) {
    double[] converted = new double[evaluated.length];
    for (int i = 0; i < evaluated.length; i++) { converted[i] = (double) evaluated[i]; }
    return converted;
  }

  @ExplodeLoop
  private boolean[] convertToBooleanArray(final Object[] evaluated) {
    boolean[] converted = new boolean[evaluated.length];
    for (int i = 0; i < evaluated.length; i++) { converted[i] = (boolean) evaluated[i]; }
    return converted;
  }

  private Object executeAndSpecialize(final VirtualFrame frame) {
    Object[] evaluated = evaluateAsArray(frame);

    if (headSameTypeAsTail(evaluated)) {
      if (evaluated[0] instanceof Long) {
        return convertToLongArray(evaluated);
      } else if (evaluated[0] instanceof Double) {
        return convertToDoubleArray(evaluated);
      } else if (evaluated[0] instanceof Boolean) {
        return convertToBooleanArray(evaluated);
      }
    }

    return evaluated;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return new SMutableArray(executeAndSpecialize(frame), Classes.arrayClass);
  }
}

