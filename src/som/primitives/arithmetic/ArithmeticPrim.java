package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.BinaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;


public abstract class ArithmeticPrim extends BinaryMessageNode {

  protected ArithmeticPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }

  protected SAbstractObject makeInt(final long result) {
    // Check with integer bounds and push:
    if (result > Integer.MAX_VALUE
        || result < Integer.MIN_VALUE) {
      return universe.newBigInteger(result);
    } else {
      return universe.newInteger((int) result);
    }
  }

  protected SAbstractObject makeInt(final BigInteger result) {
    if (result.bitLength() > 31) {
      return universe.newBigInteger(result);
    } else {
      return universe.newInteger(result.intValue());
    }
  }
}
