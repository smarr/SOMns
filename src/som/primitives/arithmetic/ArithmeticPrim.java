package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.BinaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SSymbol;


public abstract class ArithmeticPrim extends BinaryMessageNode {

  protected ArithmeticPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }

  protected final Object intOrBigInt(final long val) {
    if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
      return BigInteger.valueOf(val);
    } else {
      return (int) val;
    }
  }

  protected final Object reduceToIntIfPossible(final BigInteger result) {
    if (result.bitLength() > 31) {
      return result;
    } else {
      return result.intValue();
    }
  }
}
