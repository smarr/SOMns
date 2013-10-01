package som.interpreter.nodes.literals;

import som.vm.Universe;
import som.vmobjects.Object;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class IntegerLiteralNode extends LiteralNode {

  private final int value;

  public IntegerLiteralNode(final int value) {
    this.value = value;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frameValue) {
    return Universe.current().newInteger(this.doInteger());
  }

  @Specialization
  protected int doInteger() {
    return value;
  }
}
