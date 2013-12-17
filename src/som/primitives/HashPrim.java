package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class HashPrim extends UnaryMessageNode {
  public HashPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public HashPrim(final HashPrim prim) { this(prim.selector, prim.universe); }

  @Specialization
  public int doSString(final String receiver) {
    return receiver.hashCode();
  }

  @Specialization
  public int doSAbstractObject(final SAbstractObject receiver) {
    return receiver.hashCode();
  }
}
