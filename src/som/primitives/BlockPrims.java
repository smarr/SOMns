package som.primitives;

import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedValuePrimDispatchNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
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
    @Child private AbstractDispatchNode dispatchNode;

    public ValueNonePrim() {
      super();
      dispatchNode = new UninitializedValuePrimDispatchNode();
    }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame, final SBlock receiver) {
      return dispatchNode.executeDispatch(frame, new Object[] {receiver});
    }

    public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      dispatchNode = insert(node);
    }
  }

  public abstract static class ValueOnePrim extends BinaryExpressionNode {
    @Specialization
    public final Object doSBlock(final VirtualFrame frame, final SBlock receiver,
        final Object arg) {
      return receiver.getMethod().invoke(receiver, arg);
    }
  }

  public abstract static class ValueTwoPrim extends TernaryExpressionNode {
    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2) {
      return receiver.getMethod().invoke(receiver, arg1, arg2);
    }
  }

  public abstract static class ValueMorePrim extends QuaternaryExpressionNode {
    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object firstArg, final Object secondArg,
        final Object thirdArg) {
      throw new RuntimeException("This should never be called, because SOM Blocks have max. 2 arguments.");
//      return receiver.getMethod().invoke(new Object[] {receiver, firstArg,
//          secondArg, thirdArg});
    }
  }
}
