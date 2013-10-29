package som.primitives;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.PackedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class IntegerPrims extends PrimitiveNode {

  protected IntegerPrims(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  protected SAbstractObject makeInt(final long result) {
    // Check with integer bounds and push:
    if (result > Integer.MAX_VALUE
        || result < Integer.MIN_VALUE) {
      return universe.newBigInteger(result);
    } else {
      return universe.newInteger((int) result);
    }
  }

  protected SAbstractObject resendAsBigInteger(final String operator, final SInteger left,
      final SBigInteger right, final PackedFrame frame) {
    // Construct left value as BigInteger:
    SBigInteger leftBigInteger = universe.
        newBigInteger(left.getEmbeddedInteger());

    // Resend message:
    SAbstractObject[] operands = new SAbstractObject[1];
    operands[0] = right;

    return leftBigInteger.send(operator, operands, universe, frame);
  }

  protected SAbstractObject resendAsDouble(final java.lang.String operator, final SInteger left, final SDouble right,
      final PackedFrame frame) {
    SDouble leftDouble = universe.newDouble(left.getEmbeddedInteger());
    SAbstractObject[] operands = new SAbstractObject[] {right};
    return leftDouble.send(operator, operands, universe, frame);
  }

  public abstract static class AsStringPrim extends PrimitiveNode {
    public AsStringPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SInteger self = (SInteger) receiver;
      return universe.newString(Integer.toString(
          self.getEmbeddedInteger()));
    }
  }

  public abstract static class RandomPrim extends PrimitiveNode {
    public RandomPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SInteger self = (SInteger) receiver;
      return universe.newInteger(
          (int) (self.getEmbeddedInteger() * Math.random()));
    }
  }

  public abstract static class DoubleDivPrim extends IntegerPrims {
    public DoubleDivPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SAbstractObject rightObj = ((SAbstractObject[]) arguments)[0];
      SInteger left = (SInteger) receiver;

      // Check second parameter type:
      if (rightObj instanceof SBigInteger) {
        // Second operand was BigInteger
        return resendAsBigInteger("/", left, (SBigInteger) rightObj, frame.pack());
      } else if (rightObj instanceof SDouble) {
        return resendAsDouble("/", left, (SDouble) rightObj, frame.pack());
      } else {
        // Do operation:
        SInteger right = (SInteger) rightObj;

        double result = ((double) left.getEmbeddedInteger())
            / right.getEmbeddedInteger();
        return universe.newDouble(result);
      }
    }
  }

  public abstract static class DivPrim extends IntegerPrims {
    public DivPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SAbstractObject rightObj = ((SAbstractObject[]) arguments)[0];
      SInteger left = (SInteger) receiver;

      // Check second parameter type:
      if (rightObj instanceof SBigInteger) {
        // Second operand was BigInteger
        return resendAsBigInteger("/", left, (SBigInteger) rightObj, frame.pack());
      } else if (rightObj instanceof SDouble) {
        return resendAsDouble("/", left, (SDouble) rightObj, frame.pack());
      } else {
        // Do operation:
        SInteger right = (SInteger) rightObj;

        long result = ((long) left.getEmbeddedInteger())
            / right.getEmbeddedInteger();
        return makeInt(result);
      }
    }
  }

  public abstract static class ModPrim extends IntegerPrims {
    public ModPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SAbstractObject rightObj = ((SAbstractObject[]) arguments)[0];
      SInteger left = (SInteger) receiver;

      // Check second parameter type:
      if (rightObj instanceof SBigInteger) {
        // Second operand was BigInteger
        return resendAsBigInteger("%", left, (SBigInteger) rightObj, frame.pack());
      } else if (rightObj instanceof SDouble) {
        return resendAsDouble("%", left, (SDouble) rightObj, frame.pack());
      } else {
        // Do operation:
        SInteger right = (SInteger) rightObj;

        long l = left.getEmbeddedInteger();
        long r = right.getEmbeddedInteger();
        long result = l % r;

        if (l > 0 && r < 0) {
          result += r;
        }

        return makeInt(result);
      }
    }
  }

  public abstract static class EqualsPrim extends IntegerPrims {
    public EqualsPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SAbstractObject rightObj = ((SAbstractObject[]) arguments)[0];
      SInteger left = (SInteger) receiver;

      // Check second parameter type:
      if (rightObj instanceof SBigInteger) {
        // Second operand was BigInteger:
        return resendAsBigInteger("=", left, (SBigInteger) rightObj, frame.pack());
      } else if (rightObj instanceof SInteger) {
        // Second operand was Integer:
        SInteger right = (SInteger) rightObj;

        if (left.getEmbeddedInteger() == right.getEmbeddedInteger()) {
          return universe.trueObject;
        } else {
          return universe.falseObject;
        }
      } else if (rightObj instanceof SDouble) {
        // Second operand was Integer:
        SDouble right = (SDouble) rightObj;

        if (left.getEmbeddedInteger() == right.getEmbeddedDouble()) {
          return universe.trueObject;
        } else {
          return universe.falseObject;
        }
      } else {
        return universe.falseObject;
      }
    }
  }

  public abstract static class FromStringPrim extends IntegerPrims {
    public FromStringPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SString param = (SString) ((SAbstractObject[]) arguments)[0];

      long result = Long.parseLong(param.getEmbeddedString());
      return makeInt(result);
    }
  }
}
