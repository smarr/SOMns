package som.primitives.arithmetic;

import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBigInteger;
import som.vmobjects.SClass;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class SqrtPrim extends UnaryMonomorphicNode {
  public SqrtPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public SqrtPrim(final SqrtPrim node) { this(node.selector, node.universe, node.rcvrClass, node.invokable); }

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
  public SAbstractObject doSInteger(final SInteger receiver) {
    double result = Math.sqrt(receiver.getEmbeddedInteger());

    if (result == Math.rint(result)) {
      return makeInt((long) result);
    } else {
      return universe.newDouble(result);
    }
  }

  @Specialization
  public SAbstractObject doSBigInteger(final SBigInteger receiver) {
    return universe.newDouble(Math.sqrt(receiver.getEmbeddedBiginteger().doubleValue()));
  }

  @Specialization
  public SAbstractObject doSDouble(final SDouble receiver) {
    return universe.newDouble(Math.sqrt(receiver.getEmbeddedDouble()));
  }
}
