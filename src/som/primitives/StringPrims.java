package som.primitives;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.dsl.Specialization;


public class StringPrims {

  public abstract static class ConcatPrim extends BinaryExpressionNode {
    @Specialization
    public final String doSString(final String receiver, final String argument) {
      return receiver + argument;
    }
  }

  public abstract static class AsSymbolPrim extends UnaryExpressionNode {
    private final Universe universe;
    public AsSymbolPrim() { this.universe = Universe.current(); }

    @Specialization
    public final SAbstractObject doSString(final String receiver) {
      return universe.symbolFor(receiver);
    }
  }

  public abstract static class SubstringPrim extends TernaryExpressionNode {
    @Specialization
    public final String doSString(final String receiver, final long start,
        final long end) {
      try {
        return receiver.substring((int) start - 1, (int) end);
      } catch (IndexOutOfBoundsException e) {
        return "Error - index out of bounds";
      }
    }
  }
}
