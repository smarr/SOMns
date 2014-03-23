package som.primitives;

import som.interpreter.Types;
import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;


public final class ArrayPrims {
  public abstract static class AtPrim extends BinarySideEffectFreeExpressionNode {
    @Specialization
    public final Object doSArray(final SArray receiver, final int argument) {
      return receiver.getIndexableField(argument - 1);
    }
  }

  public abstract static class AtPutPrim extends TernaryExpressionNode {
    private final Universe universe;
    public AtPutPrim() { this.universe = Universe.current(); }

    @Specialization
    public final SAbstractObject doSArray(final SArray receiver, final int index, final SAbstractObject value) {
      receiver.setIndexableField(index - 1, value);
      return value;
    }

    @Specialization
    public final SAbstractObject doSArray(final SArray receiver, final int index, final Object value) {
      SAbstractObject val = Types.asAbstractObject(value, universe);
      receiver.setIndexableField(index - 1, val);
      return val;
    }
  }

  public abstract static class NewPrim extends BinarySideEffectFreeExpressionNode {
    private final Universe universe;
    public NewPrim() { this.universe = Universe.current(); }

    protected final boolean receiverIsArrayClass(final SClass receiver) {
      return receiver == universe.arrayClass;
    }

    @Specialization(guards = "receiverIsArrayClass")
    public final SAbstractObject doSClass(final SClass receiver, final int length) {
      return universe.newArray(length);
    }
  }
}
