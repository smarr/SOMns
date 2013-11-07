package som.primitives;

import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SBigInteger;
import som.vmobjects.SClass;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class AsStringPrim extends UnaryMonomorphicNode {
  public AsStringPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
  public AsStringPrim(final AsStringPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

  @Specialization // (guards = "isCachedReceiverClass")
  public Object doSSymbol(final SSymbol receiver) {
    return universe.newString(receiver.getString());
  }

  @Specialization // (guards = "isCachedReceiverClass")
  public Object doSInteger(final SInteger receiver) {
    return universe.newString(Integer.toString(
        receiver.getEmbeddedInteger()));
  }

  @Specialization // (guards = "isCachedReceiverClass")
  public Object doSDouble(final SDouble receiver) {
    return universe.newString(java.lang.Double.toString(
        receiver.getEmbeddedDouble()));
  }

  @Specialization // (guards = "isCachedReceiverClass") redundant!!
  public Object doSBigInteger(final SBigInteger receiver) {
    return universe.newString(receiver.getEmbeddedBiginteger().toString());
  }
}
