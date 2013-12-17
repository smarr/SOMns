package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class LengthPrim extends UnaryMessageNode {
  public LengthPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public LengthPrim(final LengthPrim prim)  { this(prim.selector, prim.universe); }

  @Specialization
  public int doSArray(final SArray receiver) {
    return receiver.getNumberOfIndexableFields();
  }

  @Specialization
  public int doSString(final String receiver) {
    return receiver.length();
  }
}
