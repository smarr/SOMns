package som.interpreter.nodes.literals;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class BigIntegerLiteralNode extends LiteralNode {

  private final BigInteger value;

  public BigIntegerLiteralNode(final BigInteger value) {
    this.value = value;
  }

  @Specialization
  protected BigInteger doBigInteger() {
    return value;
  }
}
