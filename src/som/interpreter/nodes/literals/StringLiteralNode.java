package som.interpreter.nodes.literals;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class StringLiteralNode extends LiteralNode {

  private final String value;

  public StringLiteralNode(final String value) {
    this.value = value;
  }

  @Specialization
  protected String doString() {
    return value;
  }

}
