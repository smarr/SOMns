package som.primitives;

import java.util.Arrays;

import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;


public final class ArrayPrims {
  public abstract static class AtPrim extends BinarySideEffectFreeExpressionNode {
    @Specialization
    public final Object doSArray(final Object[] receiver, final long argument) {
      return receiver[(int) argument - 1];
    }
  }

  public abstract static class AtPutPrim extends TernaryExpressionNode {
    @Specialization
    public final Object doSArray(final Object[] receiver, final long index, final Object value) {
      receiver[(int) index - 1] = value;
      return value;
    }
  }

  public abstract static class NewPrim extends BinarySideEffectFreeExpressionNode {

    protected final boolean receiverIsArrayClass(final SClass receiver) {
      return receiver == Classes.arrayClass;
    }

    @Specialization(guards = "receiverIsArrayClass")
    public final Object[] doSClass(final SClass receiver, final long length) {
      Object[] result = new Object[(int) length];
      Arrays.fill(result, Nil.nilObject);
      return result;
    }
  }
}
