package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class SqrtPrim extends UnaryMessageNode {
  public SqrtPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public SqrtPrim(final SqrtPrim node) { this(node.selector, node.universe); }

  private Number intOrBigInt(final long val) {
    if (val > Integer.MAX_VALUE || val < Integer.MIN_VALUE) {
      return BigInteger.valueOf(val);
    } else {
      return (int) val;
    }
  }

  @Specialization
  public Object doInteger(final int receiver) {
    double result = Math.sqrt(receiver);

    if (result == Math.rint(result)) {
      return intOrBigInt((long) result);
    } else {
      return result;
    }
  }

  @Specialization
  public double doBigInteger(final BigInteger receiver) {
    return Math.sqrt(receiver.doubleValue());
  }

  @Specialization
  public double doDouble(final double receiver) {
    return Math.sqrt(receiver);
  }
}
