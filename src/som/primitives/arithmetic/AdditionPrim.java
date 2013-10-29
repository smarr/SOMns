package som.primitives.arithmetic;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class AdditionPrim extends ArithmeticPrim {

  public AdditionPrim(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public AdditionPrim(final AdditionPrim node) {
    this(node.selector, node.universe);
  }

  @Specialization
  public SAbstractObject doSInteger(final VirtualFrame frame, final SInteger left,
      final Object arguments) {
    SAbstractObject rightObj = ((SAbstractObject[]) arguments)[0];

    // Check second parameter type:
    if (rightObj instanceof SBigInteger) {
      // Second operand was BigInteger
      return resendAsBigInteger("+", left, (SBigInteger) rightObj, frame.pack());
    } else if (rightObj instanceof SDouble) {
      return resendAsDouble("+", left, (SDouble) rightObj, frame.pack());
    } else {
      // Do operation:
      SInteger right = (SInteger) rightObj;

      long result = ((long) left.getEmbeddedInteger())
          + right.getEmbeddedInteger();
      return makeInt(result);
    }
  }

  @Specialization
  public SAbstractObject doSBigInteger(final VirtualFrame frame, final SBigInteger left,
      final Object arguments) {
    SAbstractObject rightObj = ((SAbstractObject[]) arguments)[0];
    SBigInteger right = null;

    // Check second parameter type:
    if (rightObj instanceof SInteger) {
      // Second operand was Integer
      right = universe.newBigInteger(
          ((SInteger) rightObj).getEmbeddedInteger());
    } else {
      right = (SBigInteger) rightObj;
    }

    // Do operation and perform conversion to Integer if required
    java.math.BigInteger result = left.getEmbeddedBiginteger().add(
        right.getEmbeddedBiginteger());
    if (result.bitLength() > 31) {
      return universe.newBigInteger(result);
    } else {
      return universe.newInteger(result.intValue());
    }
  }

  @Specialization
  public SAbstractObject doSDouble(final VirtualFrame frame, final SDouble left,
      final Object arguments) {
    SDouble op1 = coerceToDouble(((SAbstractObject[]) arguments)[0]);

    return universe.newDouble(op1.getEmbeddedDouble()
        + left.getEmbeddedDouble());
  }
}
