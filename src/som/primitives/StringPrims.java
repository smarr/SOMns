package som.primitives;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public class StringPrims {

  public abstract static class ConcatPrim extends BinaryExpressionNode {
    @Specialization
    public String doSString(final String receiver, final String argument) {
      return receiver + argument;
    }
    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  public abstract static class AsSymbolPrim extends UnaryExpressionNode {
    private final Universe universe;
    public AsSymbolPrim() { this.universe = Universe.current(); }

    @Specialization
    public SAbstractObject doSString(final String receiver) {
      return universe.symbolFor(receiver);
    }
    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  public abstract static class SubstringPrim extends TernaryExpressionNode {
    @Specialization
    public String doSString(final String receiver, final int start, final int end) {
      try {
        return receiver.substring(start - 1, end);
      } catch (IndexOutOfBoundsException e) {
        return "Error - index out of bounds";
      }
    }
    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }
}
