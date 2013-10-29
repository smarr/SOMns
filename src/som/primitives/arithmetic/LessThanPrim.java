package som.primitives.arithmetic;

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class LessThanPrim extends ArithmeticPrim {

  public LessThanPrim(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public LessThanPrim(final LessThanPrim node) {
    this(node.selector, node.universe);
  }


  @Specialization
  public SAbstractObject doSInteger(final VirtualFrame frame, final SInteger left,
      final Object arguments) {
    SAbstractObject rightObj = ((SAbstractObject[]) arguments)[0];

    // Check second parameter type:
    if (rightObj instanceof SBigInteger) {
      // Second operand was BigInteger
      return resendAsBigInteger("<", left, (SBigInteger) rightObj, frame.pack());
    } else if (rightObj instanceof SDouble) {
      return resendAsDouble("<", left, (SDouble) rightObj, frame.pack());
    } else {
      // Do operation:
      SInteger right = (SInteger) rightObj;

      if (left.getEmbeddedInteger() < right.getEmbeddedInteger()) {
        return universe.trueObject;
      } else {
        return universe.falseObject;
      }
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

    // Do operation:
    if (left.getEmbeddedBiginteger().compareTo(
        right.getEmbeddedBiginteger()) < 0) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }

  @Specialization
  public SAbstractObject doSDouble(final VirtualFrame frame,
      final SDouble left, final Object arguments) {
    SDouble op1 = coerceToDouble(((SAbstractObject[]) arguments)[0]);

    if (left.getEmbeddedDouble() < op1.getEmbeddedDouble()) {
      return universe.trueObject;
    } else {
      return universe.falseObject;
    }
  }
}
