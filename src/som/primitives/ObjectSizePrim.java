package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SArray;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class ObjectSizePrim extends UnaryExpressionNode {
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
