package som.interpreter.nodes.literals;

import java.math.BigInteger;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class BigIntegerLiteralNode extends LiteralNode {

  private final BigInteger value;

  public BigIntegerLiteralNode(final BigInteger value,
      final SourceSection source) {
    super(source);
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
