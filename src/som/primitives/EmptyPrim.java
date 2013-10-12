package som.primitives;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class EmptyPrim extends PrimitiveNode {
  public EmptyPrim(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  @Specialization
  public SObject doGeneric(final VirtualFrame frame, final SObject receiver,
      final Object arguments) {
    Universe.println("Warning: undefined primitive "
        + this.selector.getString() + " called");
    return null;
  }
}
