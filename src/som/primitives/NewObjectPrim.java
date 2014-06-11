package som.primitives;

import static som.vmobjects.SDomain.getDomainForNewObjects;
import som.interpreter.SArguments;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class NewObjectPrim extends UnarySideEffectFreeExpressionNode {
  private final Universe universe;
  public NewObjectPrim() { super(false); /* TODO: enforced!!! */ this.universe = Universe.current(); }

  @Specialization
  public final SAbstractObject doSClass(final VirtualFrame frame, final SClass receiver) {
    SObject domain = SArguments.domain(frame);

    return universe.newInstance(receiver, getDomainForNewObjects(domain));
  }
}
