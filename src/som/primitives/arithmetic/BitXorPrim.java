package som.primitives.arithmetic;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class BitXorPrim extends ArithmeticPrim {
  public BitXorPrim(final boolean executesEnforced) { super(executesEnforced); }
  public BitXorPrim(final BitXorPrim node) { this(node.executesEnforced); }

  @Specialization
  public final long doLong(final long receiver, final long right) {
    return receiver ^ right;
  }
}
