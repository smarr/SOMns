package som.interpreter.nodes.literals;

import som.vm.Universe;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class StringLiteralNode extends LiteralNode {

  private final String value;

  public StringLiteralNode(final String value) {
    this.value = value;
  }

  @Override
  public SObject executeGeneric(final VirtualFrame frameValue) {
    return Universe.current().newString(this.doString());
  }

  @Specialization
  protected String doString() {
    return value;
  }

}
