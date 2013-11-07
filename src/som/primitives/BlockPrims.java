package som.primitives;

import som.interpreter.RestartLoopException;
import som.interpreter.nodes.messages.BinaryMonomorphicNode;
import som.interpreter.nodes.messages.KeywordMonomorphicNode;
import som.interpreter.nodes.messages.TernaryMonomorphicNode;
import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class BlockPrims {

  public abstract static class RestartPrim extends UnaryMonomorphicNode {
    public RestartPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public RestartPrim(final RestartPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSBlock(final SBlock receiver) {
      throw new RestartLoopException();
    }
  }

  public abstract static class ValueNonePrim extends UnaryMonomorphicNode {
    public ValueNonePrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public ValueNonePrim(final ValueNonePrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSBlock(final VirtualFrame frame, final SBlock receiver) {
      return receiver.getMethod().invoke(frame.pack(), receiver, noArgs);
    }
  }

  public abstract static class ValueOnePrim extends BinaryMonomorphicNode {
    public ValueOnePrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public ValueOnePrim(final ValueOnePrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSBlock(final VirtualFrame frame,
        final SBlock receiver, final SAbstractObject arg) {
      return receiver.getMethod().invoke(frame.pack(), receiver, new SAbstractObject[] {arg});
    }
  }

  public abstract static class ValueTwoPrim extends TernaryMonomorphicNode {
    public ValueTwoPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public ValueTwoPrim(final ValueTwoPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSBlock(final VirtualFrame frame,
        final SBlock receiver, final SAbstractObject arg1, final SAbstractObject arg2) {
      return receiver.getMethod().invoke(frame.pack(), receiver, new SAbstractObject[] {arg1, arg2});
    }
  }

  public abstract static class ValueMorePrim extends KeywordMonomorphicNode {
    public ValueMorePrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public ValueMorePrim(final ValueMorePrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arguments) {
      return receiver.getMethod().invoke(frame.pack(), receiver, (SAbstractObject[]) arguments);
    }
  }
}
