package som.primitives;

import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedValuePrimDispatchNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class BlockPrims {

  public interface ValuePrimitiveNode {
    void adoptNewDispatchListHead(final AbstractDispatchNode node);
  }

  public abstract static class RestartPrim extends UnaryExpressionNode {

    @Specialization
    public SAbstractObject doSBlock(final SBlock receiver) {
      CompilerDirectives.transferToInterpreter();
      // TruffleSOM intrinsifies #whileTrue: and #whileFalse:
      throw new RuntimeException("This primitive is not supported anymore! "
          + "Something went wrong with the intrinsification of "
          + "#whileTrue:/#whileFalse:?");
    }
  }

  public abstract static class ValueNonePrim extends UnaryExpressionNode
      implements ValuePrimitiveNode {
    @Child private AbstractDispatchNode dispatchNode;

    public ValueNonePrim() {
      super();
      dispatchNode = new UninitializedValuePrimDispatchNode();
    }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame, final SBlock receiver) {
      return dispatchNode.executeDispatch(frame, new Object[] {receiver});
    }

    @Override
    public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      dispatchNode = insert(node);
    }
  }

  public abstract static class ValueOnePrim extends BinaryExpressionNode
      implements ValuePrimitiveNode  {
    @Child private AbstractDispatchNode dispatchNode;

    public ValueOnePrim() {
      super();
      dispatchNode = new UninitializedValuePrimDispatchNode();
    }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame, final SBlock receiver,
        final Object arg) {
      return dispatchNode.executeDispatch(frame, new Object[] {receiver, arg});
    }

    @Override
    public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      dispatchNode = insert(node);
    }
  }

  public abstract static class ValueTwoPrim extends TernaryExpressionNode
      implements ValuePrimitiveNode {
    @Child private AbstractDispatchNode dispatchNode;

    public ValueTwoPrim() {
      super();
      dispatchNode = new UninitializedValuePrimDispatchNode();
    }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2) {
      return dispatchNode.executeDispatch(frame, new Object[] {receiver, arg1, arg2});
    }

    @Override
    public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      dispatchNode = insert(node);
    }
  }

  public abstract static class ValueMorePrim extends QuaternaryExpressionNode {
    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object firstArg, final Object secondArg,
        final Object thirdArg) {
      CompilerDirectives.transferToInterpreter();
      throw new RuntimeException("This should never be called, because SOM Blocks have max. 2 arguments.");
    }
  }
}
