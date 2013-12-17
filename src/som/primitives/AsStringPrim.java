package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
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
  public Object doInteger(final int receiver) {
    return universe.newString(Integer.toString(receiver));
  }

  @Specialization
  public Object doDouble(final double receiver) {
    return universe.newString(Double.toString(receiver));
  }

  @Specialization
  public Object doBigInteger(final BigInteger receiver) {
    return universe.newString(receiver.toString());
  }
}
