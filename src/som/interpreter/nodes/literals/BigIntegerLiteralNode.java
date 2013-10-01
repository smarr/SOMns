package som.interpreter.nodes.literals;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class BigIntegerLiteralNode extends LiteralNode {

  private final BigInteger value;

  public BigIntegerLiteralNode(final BigInteger value) {
    this.value = value;
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frameValue) {
    return Universe.current().newBigInteger(this.doBigInteger());
  }

  @Specialization
  protected BigInteger doBigInteger() {
    return value;
  }
}
