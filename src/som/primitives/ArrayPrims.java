package som.primitives;

import som.interpreter.Types;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class ArrayPrims {
  public abstract static class AtPrim extends BinaryExpressionNode {
    @Specialization
    public Object doSArray(final SArray receiver, final int argument) {
      return receiver.getIndexableField(argument - 1);
    }

    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  public abstract static class AtPutPrim extends TernaryExpressionNode {
    private final Universe universe;
    public AtPutPrim() { this.universe = Universe.current(); }

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

  public abstract static class NewPrim extends BinaryExpressionNode {
    private final Universe universe;
    public NewPrim() { this.universe = Universe.current(); }

    protected boolean receiverIsArrayClass(final SClass receiver) {
      return receiver == universe.arrayClass;
    }

    @Specialization(guards = "receiverIsArrayClass")
    public SAbstractObject doSClass(final SClass receiver, final int length) {
      return universe.newArray(length);
    }

    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }
}
