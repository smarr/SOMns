package som.primitives.arithmetic;

import som.vm.Universe;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class LogicAndPrim extends ArithmeticPrim {

  public LogicAndPrim(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public LogicAndPrim(final LogicAndPrim node) {
    this(node.selector, node.universe);
  }

  @Specialization
  public SObject doSInteger(final VirtualFrame frame, final SInteger left,
      final Object arguments) {
    SObject rightObj = ((SObject[]) arguments)[0];

    // Check second parameter type:
    if (rightObj instanceof SBigInteger) {
      // Second operand was BigInteger
      return resendAsBigInteger("&", left, (SBigInteger) rightObj, frame.pack());
    } else if (rightObj instanceof SDouble) {
      return resendAsDouble("&", left, (SDouble) rightObj, frame.pack());
    } else {
      // Do operation:
      SInteger right = (SInteger) rightObj;

      long result = ((long) left.getEmbeddedInteger())
          & right.getEmbeddedInteger();
      return makeInt(result);
    }
  }

  @Specialization
  public SObject doSBigInteger(final VirtualFrame frame, final SBigInteger left,
      final Object arguments) {
    SObject rightObj = ((SObject[]) arguments)[0];
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
    return universe.newBigInteger(left.getEmbeddedBiginteger().and(
        right.getEmbeddedBiginteger()));
  }
}
