package som.primitives;

import som.interpreter.nodes.messages.BinaryMonomorphicNode;
import som.interpreter.nodes.messages.TernaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public class ArrayPrims {
  public abstract static class AtPrim extends BinaryMonomorphicNode {

    public AtPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public AtPrim(final AtPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public Object doSArray(final SArray receiver, final int argument) {
      return receiver.getIndexableField(argument - 1);
    }

    @Specialization
    public SAbstractObject doSArray(final SArray receiver, final SInteger argument) {
      return receiver.getIndexableField(argument.getEmbeddedInteger() - 1);
    }
  }

  public abstract static class AtPutPrim extends TernaryMonomorphicNode {
    public AtPutPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public AtPutPrim(final AtPutPrim prim)  { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSArray(final SArray receiver, final SInteger index, final SAbstractObject value) {
      receiver.setIndexableField(index.getEmbeddedInteger() - 1, value);
      return value;
    }

    @Specialization
    public SAbstractObject doSArray(final SArray receiver, final int index, final SAbstractObject value) {
      receiver.setIndexableField(index - 1, value);
      return value;
    }
  }

  public abstract static class NewPrim extends BinaryMonomorphicNode {
    public NewPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public NewPrim(final NewPrim prim)  { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSArray(final SArray receiver, final SInteger length) {
      return universe.newArray(length.getEmbeddedInteger());
    }
  }

}
