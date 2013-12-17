package som.primitives;

import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class ObjectSizePrim extends UnaryMessageNode {
  public ObjectSizePrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
  public ObjectSizePrim(final ObjectSizePrim prim) { this(prim.selector, prim.universe); }

  @Specialization
  public int doSArray(final SArray receiver) {
    int size = 0;
    size += receiver.getNumberOfIndexableFields();
    return size;
  }

  @Specialization
  public int doSObject(final SObject receiver) {
    int size = 0;
    size += receiver.getNumberOfFields();
    return size;
  }

  @Specialization
  public int doSAbstractObject(final Object receiver) {
    return 0; // TODO: allow polymorphism?
  }
}
