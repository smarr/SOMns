package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class NewObjectPrim extends UnaryMessageNode {
  public NewObjectPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public NewObjectPrim(final NewObjectPrim prim) { this(prim.selector, prim.universe); }

  @Specialization
  public SAbstractObject doSClass(final SClass receiver) {
    return universe.newInstance(receiver);
  }
}
