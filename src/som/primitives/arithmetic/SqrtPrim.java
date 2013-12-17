package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class SqrtPrim extends UnaryMessageNode {
  public SqrtPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public SqrtPrim(final SqrtPrim node) { this(node.selector, node.universe); }

  protected SAbstractObject makeInt(final long result) {
    // Check with integer bounds and push:
    if (result > Integer.MAX_VALUE
        || result < Integer.MIN_VALUE) {
      return universe.newBigInteger(result);
    } else {
      return universe.newInteger((int) result);
    }
  }

  @Specialization
  public SAbstractObject doInteger(final int receiver) {
    double result = Math.sqrt(receiver);

    if (result == Math.rint(result)) {
      return makeInt((long) result);
    } else {
      return universe.newDouble(result);
    }
  }

  @Specialization
  public SAbstractObject doBigInteger(final BigInteger receiver) {
    return universe.newDouble(Math.sqrt(receiver.doubleValue()));
  }

  @Specialization
  public SAbstractObject doDouble(final double receiver) {
    return universe.newDouble(Math.sqrt(receiver));
  }
}
