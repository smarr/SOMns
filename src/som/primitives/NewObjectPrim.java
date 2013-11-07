package som.primitives;

import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class NewObjectPrim extends UnaryMonomorphicNode {
  public NewObjectPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public NewObjectPrim(final NewObjectPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

  @Specialization
  public SAbstractObject doSClass(final SClass receiver) {
    return universe.newInstance(receiver);
  }
}
