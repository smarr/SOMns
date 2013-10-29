package som.primitives;

import som.interpreter.nodes.PrimitiveNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class NewObjectPrim extends PrimitiveNode {
  public NewObjectPrim(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  @Specialization
  public SAbstractObject doGeneric(final VirtualFrame frame,
      final SAbstractObject receiver,
      final Object arguments) {
    return universe.newInstance((SClass) receiver);
  }

}
