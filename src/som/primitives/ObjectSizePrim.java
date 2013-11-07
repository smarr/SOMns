package som.primitives;

import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class ObjectSizePrim extends UnaryMonomorphicNode {
  public ObjectSizePrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public ObjectSizePrim(final ObjectSizePrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

  @Specialization(order = 1)
  public SAbstractObject doSArray(final SArray receiver) {
    int size = 0;
    size += receiver.getNumberOfIndexableFields();
    return universe.newInteger(size);
  }

  @Specialization(guards = "isCachedReceiverClass", order = 2)
  public SAbstractObject doSObject(final SObject receiver) {
    int size = 0;
    size += receiver.getNumberOfFields();
    return universe.newInteger(size);
  }

  @Override
  @Specialization(guards = "isCachedReceiverClass", order = 3)
  public SAbstractObject doMonomorphic(final VirtualFrame frame, final SAbstractObject receiver) {
    return universe.newInteger(0); // TODO: allow polymorphism?
  }
}
