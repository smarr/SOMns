package som.primitives;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class BlockPrims {

  public abstract static class RestartPrim extends UnaryExpressionNode {

    @Specialization
    public SAbstractObject doSBlock(final SBlock receiver) {
      // TruffleSOM intrinsifies #whileTrue: and #whileFalse:
      throw new RuntimeException("This primitive is not supported anymore! "
          + "Something went wrong with the intrinsification of "
          + "#whileTrue:/#whileFalse:?");
    }
  }

  public abstract static class ValueNonePrim extends UnaryExpressionNode {
    private final Universe universe;
    public ValueNonePrim() { this.universe = Universe.current(); }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame, final SBlock receiver) {
      return receiver.getMethod().invoke(frame.pack(), receiver, universe);
    }
  }

  public abstract static class ValueOnePrim extends BinaryExpressionNode {
    private final Universe universe;
    public ValueOnePrim() { this.universe = Universe.current(); }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame, final SBlock receiver,
        final Object arg) {
      return receiver.getMethod().invoke(frame.pack(), receiver, arg, universe);
    }
  }

  public abstract static class ValueTwoPrim extends TernaryExpressionNode {
    private final Universe universe;
    public ValueTwoPrim() { this.universe = Universe.current(); }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2) {
      return receiver.getMethod().invoke(frame.pack(), receiver, arg1, arg2, universe);
    }
  }

  public abstract static class ValueMorePrim extends QuaternaryExpressionNode {
    private final Universe universe;
    public ValueMorePrim() { this.universe = Universe.current(); }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object firstArg, final Object secondArg,
        final Object thirdArg) {
      return receiver.getMethod().invoke(frame.pack(), receiver,
          new Object[] {firstArg, secondArg, thirdArg}, universe);
    }
  }
}
