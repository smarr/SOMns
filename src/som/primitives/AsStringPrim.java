package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.SlowPath;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class AsStringPrim extends UnaryExpressionNode {

  @Specialization
  public final String doSSymbol(final SSymbol receiver) {
    return receiver.getString();
  }

  @SlowPath
  @Specialization
  public final String doInteger(final int receiver) {
    return Integer.toString(receiver);
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

  @Override
  public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
}
