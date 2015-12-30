package som.primitives;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;


public abstract class BlockPrims {


  public static final int CHAIN_LENGTH = 6;

  public static final DirectCallNode createDirectCallNode(final SBlock receiver) {
    assert null != receiver.getMethod().getCallTarget();
    return Truffle.getRuntime().createDirectCallNode(
        receiver.getMethod().getCallTarget());
  }

  public static final IndirectCallNode create() {
    return Truffle.getRuntime().createIndirectCallNode();
  }

  @GenerateNodeFactory
  @Primitive("blockRestart:")
  public abstract static class RestartPrim extends UnaryExpressionNode {
    public RestartPrim() { super(null); }

    @Specialization
    public SAbstractObject doSBlock(final SBlock receiver) {
      CompilerDirectives.transferToInterpreter();
      // TruffleSOM intrinsifies #whileTrue: and #whileFalse:
      throw new RuntimeException("This primitive is not supported anymore! "
          + "Something went wrong with the intrinsification of "
          + "#whileTrue:/#whileFalse:?");
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive("blockValue:")
  public abstract static class ValueNonePrim extends UnaryExpressionNode {

    @Specialization
    public final boolean doBoolean(final boolean receiver) {
      return receiver;
    }

    @Specialization(guards = "cached == receiver.getMethod()", limit = "CHAIN_LENGTH")
    public final Object doCachedBlock(final VirtualFrame frame,
        final SBlock receiver,
        @Cached("createDirectCallNode(receiver)") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(frame, new Object[] {receiver});
    }

    @Specialization(contains = "doCachedBlock")
    public final Object doGeneric(final VirtualFrame frame,
        final SBlock receiver, @Cached("create()") final IndirectCallNode call) {
      return receiver.getMethod().invoke(call, frame, new Object[] {receiver});
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive("blockValue:with:")
  public abstract static class ValueOnePrim extends BinaryExpressionNode {

    @Specialization(guards = "cached == receiver.getMethod()", limit = "CHAIN_LENGTH")
    public final Object doCachedBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg,
        @Cached("createDirectCallNode(receiver)") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(frame, new Object[] {receiver, arg});
    }

    @Specialization(contains = "doCachedBlock")
    public final Object doGeneric(final VirtualFrame frame,
        final SBlock receiver, final Object arg,
        @Cached("create()") final IndirectCallNode call) {
      return receiver.getMethod().invoke(call, frame, new Object[] {receiver, arg});
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive("blockValue:with:with:")
  public abstract static class ValueTwoPrim extends TernaryExpressionNode {

    @Specialization(guards = "cached == receiver.getMethod()", limit = "CHAIN_LENGTH")
    public final Object doCachedBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2,
        @Cached("createDirectCallNode(receiver)") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(frame, new Object[] {receiver, arg1, arg2});
    }

    @Specialization(contains = "doCachedBlock")
    public final Object doGeneric(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2,
        @Cached("create()") final IndirectCallNode call) {
      return receiver.getMethod().invoke(call, frame, new Object[] {receiver, arg1, arg2});
    }
  }

  @GenerateNodeFactory
  public abstract static class ValueMorePrim extends QuaternaryExpressionNode {
    public ValueMorePrim() { super(null); }
    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object firstArg, final Object secondArg,
        final Object thirdArg) {
      CompilerDirectives.transferToInterpreter();
      throw new RuntimeException("This should never be called, because SOM Blocks have max. 2 arguments.");
    }
  }
}
