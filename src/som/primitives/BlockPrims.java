package som.primitives;

import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class BlockPrims {

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

  public static final BlockDispatchNode createDispatch() {
    return BlockDispatchNodeGen.create();
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive("blockValue:")
  public abstract static class ValueNonePrim extends UnaryExpressionNode {

    @Specialization
    public final boolean doBoolean(final boolean receiver) {
      return receiver;
    }

    @Specialization
    public final Object doBlock(final VirtualFrame frame, final SBlock receiver,
        @Cached("createDispatch()") final BlockDispatchNode call) {
      return call.executeDispatch(frame, new Object[] {receiver});
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive("blockValue:with:")
  public abstract static class ValueOnePrim extends BinaryExpressionNode {

    @Specialization
    public final Object doCachedBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg,
        @Cached("createDispatch()") final BlockDispatchNode call) {
      return call.executeDispatch(frame, new Object[] {receiver, arg});
    }
  }

  @GenerateNodeFactory
  @ImportStatic(BlockPrims.class)
  @Primitive("blockValue:with:with:")
  public abstract static class ValueTwoPrim extends TernaryExpressionNode {

    @Specialization
    public final Object doCachedBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2,
        @Cached("createDispatch()") final BlockDispatchNode call) {
      return call.executeDispatch(frame, new Object[] {receiver, arg1, arg2});
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
