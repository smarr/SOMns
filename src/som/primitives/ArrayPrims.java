package som.primitives;

import som.interpreter.Types;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import som.vmobjects.SInteger;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public class ArrayPrims {
  public abstract static class AtPrim extends BinaryMessageNode {

    public AtPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public AtPrim(final AtPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public Object doSArray(final SArray receiver, final int argument) {
      return receiver.getIndexableField(argument - 1);
    }

    @Specialization
    public SAbstractObject doSArray(final SArray receiver, final SInteger argument) {
      return receiver.getIndexableField(argument.getEmbeddedInteger() - 1);
    }
  }

  public abstract static class AtPutPrim extends TernaryMessageNode {
    public AtPutPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public AtPutPrim(final AtPutPrim prim)  { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSArray(final SArray receiver, final int index, final SAbstractObject value) {
      receiver.setIndexableField(index - 1, value);
      return value;
    }

    @Specialization
    public SAbstractObject doSArray(final SArray receiver, final int index, final Object value) {
      SAbstractObject val = Types.asAbstractObject(value, universe);
      receiver.setIndexableField(index - 1, val);
      return val;
    }
  }

  public abstract static class NewPrim extends BinaryMessageNode {
    public NewPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public NewPrim(final NewPrim prim)  { this(prim.selector, prim.universe); }

    protected boolean receiverIsArrayClass(final SClass receiver) {
      return receiver == universe.arrayClass;
    }

    @Specialization(guards = "receiverIsArrayClass")
    public SAbstractObject doSClass(final SClass receiver, final SInteger length) {
      return universe.newArray(length.getEmbeddedInteger());
    }

    @Specialization(guards = "receiverIsArrayClass")
    public SAbstractObject doSClass(final SClass receiver, final int length) {
      return universe.newArray(length);
    }
  }

}
