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

  public NewObjectPrim(final boolean executesEnforced) { super(executesEnforced); }
  public NewObjectPrim(final NewObjectPrim node) { this(node.executesEnforced); }

  @Specialization
  public final SAbstractObject doSClass(final VirtualFrame frame, final SClass receiver) {
    SObject domain = SArguments.domain(frame);

    return Universe.newInstance(receiver, getDomainForNewObjects(domain));
  }
}
