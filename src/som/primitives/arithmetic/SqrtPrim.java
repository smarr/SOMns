package som.primitives.arithmetic;

import java.math.BigInteger;

import som.interpreter.nodes.nary.UnaryExpressionNode;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.BranchProfile;


public abstract class SqrtPrim extends UnaryExpressionNode {

  public SqrtPrim(final boolean executesEnforced) { super(null, executesEnforced); }
  public SqrtPrim(final SqrtPrim node) { this(node.executesEnforced); }

  private final BranchProfile longReturn   = new BranchProfile();
  private final BranchProfile doubleReturn = new BranchProfile();

  @Specialization
  public final Object doLong(final long receiver) {
    double result = Math.sqrt(receiver);

    if (result == Math.rint(result)) {
      longReturn.enter();
      return (long) result;
    } else {
      doubleReturn.enter();
      return result;
    }
  }

  @Specialization
  public final double doBigInteger(final BigInteger receiver) {
    return Math.sqrt(receiver.doubleValue());
  }

  @Specialization
  public final double doDouble(final double receiver) {
    return Math.sqrt(receiver);
  }
}
