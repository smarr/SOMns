package som.primitives;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class BigIntegerPrims {

  public abstract static class AsStringPrim extends PrimitiveNode {
    public AsStringPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SBigInteger self = (SBigInteger) receiver;
      return universe.newString(self.getEmbeddedBiginteger().toString());
    }
  }

  public abstract static class DividePrim extends PrimitiveNode {
    public DividePrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SAbstractObject rightObj = ((SAbstractObject[]) arguments)[0];
      SBigInteger right = null;
      SBigInteger left = (SBigInteger) receiver;

      // Check second parameter type:
      if (rightObj instanceof SInteger) {
        // Second operand was Integer
        right = universe.newBigInteger(
            ((SInteger) rightObj).getEmbeddedInteger());
      } else {
        right = (SBigInteger) rightObj;
      }

      // Do operation and perform conversion to Integer if required
      java.math.BigInteger result = left.getEmbeddedBiginteger().divide(
          right.getEmbeddedBiginteger());
      if (result.bitLength() > 31) {
        return universe.newBigInteger(result);
      } else {
        return universe.newInteger(result.intValue());
      }
    }
  }


  public abstract static class ModPrim extends PrimitiveNode {
    public ModPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SAbstractObject rightObj = ((SAbstractObject[]) arguments)[0];
      SBigInteger right = null;
      SBigInteger left = (SBigInteger) receiver;

      // Check second parameter type:
      if (rightObj instanceof SInteger) {
        // Second operand was Integer
        right = universe.newBigInteger(
            ((SInteger) rightObj).getEmbeddedInteger());
      } else {
        right = (SBigInteger) rightObj;
      }

      // Do operation:
      return universe.newBigInteger(left.getEmbeddedBiginteger().mod(
          right.getEmbeddedBiginteger()));
    }
  }

  public abstract static class EqualsPrim extends PrimitiveNode {
    public EqualsPrim(final SSymbol selector, final Universe universe) {
      super(selector, universe);
    }

    @Specialization
    public SAbstractObject doGeneric(final VirtualFrame frame,
        final SAbstractObject receiver, final Object arguments) {
      SAbstractObject rightObj = ((SAbstractObject[]) arguments)[0];
      SBigInteger right = null;
      SBigInteger left = (SBigInteger) receiver;

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
          right.getEmbeddedBiginteger()) == 0) {
        return universe.trueObject;
      } else {
        return universe.falseObject;
      }
    }
  }
}
