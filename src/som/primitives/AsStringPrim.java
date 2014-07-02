package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.Specialization;


public abstract class AsStringPrim extends UnarySideEffectFreeExpressionNode {

  public AsStringPrim(final boolean executesEnforced) { super(executesEnforced); }
  public AsStringPrim(final AsStringPrim node) { super(node.executesEnforced); }

  @Specialization
  public final String doSSymbol(final SSymbol receiver) {
    return receiver.getString();
  }

  @SlowPath
  @Specialization
  public final String doLong(final long receiver) {
    return Long.toString(receiver);
  }

  @SlowPath
  @Specialization
  public final String doDouble(final double receiver) {
    return Double.toString(receiver);
  }

  @SlowPath
  @Specialization
  public final String doBigInteger(final BigInteger receiver) {
    return receiver.toString();
  }
}
