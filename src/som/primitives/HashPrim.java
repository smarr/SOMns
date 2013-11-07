package som.primitives;

import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class HashPrim extends UnaryMonomorphicNode {
  public HashPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public HashPrim(final HashPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

  @Specialization(order = 1)
  public SAbstractObject doSString(final SString receiver) {
    return universe.newInteger(receiver.getEmbeddedString().hashCode());
  }

  @Override
  @Specialization(guards = "isCachedReceiverClass", order = 2)
  public SAbstractObject doMonomorphic(final VirtualFrame frame, final SAbstractObject receiver) {
    return universe.newInteger(receiver.hashCode());
  }
}
