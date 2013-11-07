package som.primitives;

import som.interpreter.nodes.AbstractMessageNode;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SSymbol;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class EmptyPrim extends AbstractMessageNode {
  public EmptyPrim(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public EmptyPrim(final EmptyPrim node) {
    this(node.selector, node.universe);
  }

  @Override
  @Specialization
  public Object executeGeneric(final VirtualFrame frame) {
    Universe.println("Warning: undefined primitive "
        + this.selector.getString() + " called");
    return null;
  }

  @Override
  public ExpressionNode cloneForInlining() {
    throw new NotImplementedException();
  }
}
