package som.primitives;

import som.interpreter.RestartLoopException;
import som.interpreter.nodes.BinaryMessageNode;
import som.interpreter.nodes.KeywordMessageNode;
import som.interpreter.nodes.TernaryMessageNode;
import som.interpreter.nodes.UnaryMessageNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class BlockPrims {

  public abstract static class RestartPrim extends UnaryMessageNode {
    public RestartPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public RestartPrim(final RestartPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public SAbstractObject doSBlock(final SBlock receiver) {
      throw new RestartLoopException();
    }
  }

  public abstract static class ValueNonePrim extends UnaryMessageNode {
    public ValueNonePrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public ValueNonePrim(final ValueNonePrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public Object doSBlock(final VirtualFrame frame, final SBlock receiver) {
      return receiver.getMethod().invoke(frame.pack(), receiver, universe);
    }
  }

  public abstract static class ValueOnePrim extends BinaryMessageNode {
    public ValueOnePrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public ValueOnePrim(final ValueOnePrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public Object doSBlock(final VirtualFrame frame, final SBlock receiver,
        final Object arg) {
      return receiver.getMethod().invoke(frame.pack(), receiver, arg, universe);
    }
  }

  public abstract static class ValueTwoPrim extends TernaryMessageNode {
    public ValueTwoPrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public ValueTwoPrim(final ValueTwoPrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2) {
      return receiver.getMethod().invoke(frame.pack(), receiver, arg1, arg2, universe);
    }
  }

  public abstract static class ValueMorePrim extends KeywordMessageNode {
    public ValueMorePrim(final SSymbol selector, final Universe universe) { super(selector, universe); }
    public ValueMorePrim(final ValueMorePrim prim) { this(prim.selector, prim.universe); }

    @Specialization
    public Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object[] arguments) {
      return receiver.getMethod().invoke(frame.pack(), receiver, arguments, universe);
    }
  }
}
