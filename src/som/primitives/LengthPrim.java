package som.primitives;

import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class LengthPrim extends UnaryMonomorphicNode {
  public LengthPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public LengthPrim(final LengthPrim prim)  { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

  @Specialization
  public SAbstractObject doSArray(final SArray receiver) {
    return universe.newInteger(receiver.getNumberOfIndexableFields());
  }

  @Specialization
  public SAbstractObject doSString(final SString receiver) {
    return universe.newInteger(receiver.getEmbeddedString().length());
  }
}
