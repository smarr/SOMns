package som.interpreter.nodes.literals;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class IntegerLiteralNode extends LiteralNode {

  private final int value;

  public IntegerLiteralNode(final int value) {
    this.value = value;
  }

  @Specialization
  protected int doInteger() {
    return value;
  }
}
