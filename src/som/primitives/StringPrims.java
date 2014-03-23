package som.primitives;

import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode.TernarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.dsl.Specialization;


public class StringPrims {

  public abstract static class ConcatPrim extends BinarySideEffectFreeExpressionNode {
    @Specialization
    public final String doSString(final String receiver, final String argument) {
      return receiver + argument;
    }
  }

  public abstract static class AsSymbolPrim extends UnarySideEffectFreeExpressionNode {
    private final Universe universe;
    public AsSymbolPrim() { this.universe = Universe.current(); }

    @Specialization
    public final SAbstractObject doSString(final String receiver) {
      return universe.symbolFor(receiver);
    }
  }

  public abstract static class SubstringPrim extends TernarySideEffectFreeExpressionNode {
    @Specialization
    public final String doSString(final String receiver, final int start, final int end) {
      try {
        return receiver.substring(start - 1, end);
      } catch (IndexOutOfBoundsException e) {
        return "Error - index out of bounds";
      }
    }
  }
}
