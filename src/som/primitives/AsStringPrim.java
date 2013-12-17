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
  public String doSSymbol(final SSymbol receiver) {
    return receiver.getString();
  }

  @Specialization
  public String doInteger(final int receiver) {
    return Integer.toString(receiver);
  }

  @Specialization
  public String doDouble(final double receiver) {
    return Double.toString(receiver);
  }

  @Specialization
  public String doBigInteger(final BigInteger receiver) {
    return receiver.toString();
  }
}
