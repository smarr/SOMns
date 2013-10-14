package som.primitives.arithmetic;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.PackedFrame;


public abstract class ArithmeticPrim extends PrimitiveNode {

  protected ArithmeticPrim(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  protected SObject makeInt(final long result) {
    // Check with integer bounds and push:
    if (result > Integer.MAX_VALUE
        || result < Integer.MIN_VALUE) {
      return universe.newBigInteger(result);
    } else {
      return universe.newInteger((int) result);
    }
  }

  protected SObject resendAsBigInteger(final String operator, final SInteger left,
      final SBigInteger right, final PackedFrame frame) {
    // Construct left value as BigInteger:
    SBigInteger leftBigInteger = universe.
        newBigInteger(left.getEmbeddedInteger());

    // Resend message:
    SObject[] operands = new SObject[1];
    operands[0] = right;

    return leftBigInteger.send(operator, operands, universe, frame);
  }

  protected SObject resendAsDouble(final java.lang.String operator, final SInteger left, final SDouble right,
      final PackedFrame frame) {
    SDouble leftDouble = universe.newDouble(left.getEmbeddedInteger());
    SObject[] operands = new SObject[] {right};
    return leftDouble.send(operator, operands, universe, frame);
  }

  protected SDouble coerceToDouble(final SObject o) {
    if (o instanceof SDouble) { return (SDouble) o; }
    if (o instanceof SInteger) {
      return universe.newDouble(((SInteger) o).getEmbeddedInteger());
    }
    throw new ClassCastException("Cannot coerce to Double!");
  }
}
