package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SBigInteger;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class AsStringPrim extends UnaryMessageNode {
  public AsStringPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public AsStringPrim(final AsStringPrim prim) { this(prim.selector, prim.universe); }

  @Specialization
  public Object doSSymbol(final SSymbol receiver) {
    return universe.newString(receiver.getString());
  }

  @Specialization
  public Object doSInteger(final SInteger receiver) {
    return universe.newString(Integer.toString(
        receiver.getEmbeddedInteger()));
  }

  @Specialization
  public Object doSDouble(final SDouble receiver) {
    return universe.newString(Double.toString(
        receiver.getEmbeddedDouble()));
  }

  @Specialization
  public Object doSBigInteger(final SBigInteger receiver) {
    return universe.newString(receiver.getEmbeddedBiginteger().toString());
  }
}
