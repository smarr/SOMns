package som.primitives;

import java.util.Arrays;

import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;


public final class ArrayPrims {
  public abstract static class AtPrim extends BinarySideEffectFreeExpressionNode {
    public AtPrim() { super(false); /* TODO: enforced!!! */ }
    @Specialization
    public final Object doSArray(final Object[] receiver, final long argument) {
      return receiver[(int) argument - 1];
    }
  }

  public abstract static class AtPutPrim extends TernaryExpressionNode {
    public AtPutPrim() { super(false); /* TODO: enforced!!! */ }
    @Specialization
    public final Object doSArray(final Object[] receiver, final long index, final Object value) {
      receiver[(int) index - 1] = value;
      return value;
    }
  }

  public abstract static class NewPrim extends BinarySideEffectFreeExpressionNode {
    private final Universe universe;
    public NewPrim() { super(false);  /* TODO: enforced!!! */ this.universe = Universe.current(); }

    protected final boolean receiverIsArrayClass(final SClass receiver) {
      return receiver == universe.arrayClass;
    }

    @Specialization(guards = "receiverIsArrayClass")
    public final Object[] doSClass(final SClass receiver, final long length) {
      Object[] result = new Object[(int) length];
      Arrays.fill(result, universe.nilObject);
      return result;
    }
  }
}
