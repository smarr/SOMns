package som.interpreter.nodes.literals;

import java.math.BigInteger;

import com.oracle.truffle.api.frame.VirtualFrame;


public final class BigIntegerLiteralNode extends LiteralNode {

  private final BigInteger value;

  public BigIntegerLiteralNode(final BigInteger value) {
    this.value = value;
  }

  @Override
  public BigInteger executeBigInteger(final VirtualFrame frame) {
    return value;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    return value;
  }
}
