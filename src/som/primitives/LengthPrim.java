package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class LengthPrim extends UnaryMessageNode {
  public LengthPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public LengthPrim(final LengthPrim prim)  { this(prim.selector, prim.universe); }

  @Specialization
  public SAbstractObject doSArray(final SArray receiver) {
    return universe.newInteger(receiver.getNumberOfIndexableFields());
  }

  @Specialization
  public SAbstractObject doSString(final SString receiver) {
    return universe.newInteger(receiver.getEmbeddedString().length());
  }
}
