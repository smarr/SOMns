package som.interpreter.nodes.literals;

import static com.oracle.truffle.api.nodes.NodeInfo.Kind.SPECIALIZED;

import java.math.BigInteger;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;


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

  @Override
  public Kind getKind() {
      return SPECIALIZED;
  }
}
