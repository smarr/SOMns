package som.primitives;

import som.interpreter.SomException;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedValuePrimDispatchNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.BlockPrims.ValuePrimitiveNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;


public abstract class ExceptionsPrims {

  @GenerateNodeFactory
  @Primitive("exceptionDo:catch:onException:")
  public abstract static class ExceptionDoOnPrim extends TernaryExpressionNode implements ValuePrimitiveNode {

    @Child private AbstractDispatchNode dispatchBody    = new UninitializedValuePrimDispatchNode();
    @Child private AbstractDispatchNode dispatchHandler = new UninitializedValuePrimDispatchNode();

    @Specialization
    public final Object doException(final VirtualFrame frame, final SBlock body,
        final SClass exceptionClass, final SBlock exceptionHandler) {
      try {
        return dispatchBody.executeDispatch(frame, new Object[] {body});
      } catch (SomException e) {
        if (e.getSomObject().getSOMClass() == exceptionClass) {
          return dispatchHandler.executeDispatch(frame,
              new Object[] {exceptionHandler, e.getSomObject()});
        } else {
          throw e;
        }
      }
    }

    @Override
    public final void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      throw new RuntimeException("This is not supported. Need to use a "
          + "different way, perhaps the DSL instead. but the "
          + "ValuePrimitiveNode interface doesn't work for two blocks.");
    }

    @Override
    public NodeCost getCost() {
      int dispatchChain = dispatchBody.lengthOfDispatchChain();
      if (dispatchChain == 0) {
        return NodeCost.UNINITIALIZED;
      } else if (dispatchChain == 1) {
        return NodeCost.MONOMORPHIC;
      } else if (dispatchChain <= AbstractDispatchNode.INLINE_CACHE_SIZE) {
        return NodeCost.POLYMORPHIC;
      } else {
        return NodeCost.MEGAMORPHIC;
      }
    }
  }


  @GenerateNodeFactory
  @Primitive("signalException:")
  public abstract static class SignalPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSignal(final SAbstractObject exceptionObject) {
      throw new SomException(exceptionObject);
    }
  }

  @GenerateNodeFactory
  @Primitive("exceptionDo:ensure:")
  public abstract static class EnsurePrim extends BinaryExpressionNode implements ValuePrimitiveNode {

    @Child private AbstractDispatchNode dispatchBody    = new UninitializedValuePrimDispatchNode();
    @Child private AbstractDispatchNode dispatchHandler = new UninitializedValuePrimDispatchNode();

    @Specialization
    public final Object doException(final VirtualFrame frame, final SBlock body,
        final SBlock ensureHandler) {
      try {
        return dispatchBody.executeDispatch(frame, new Object[] {body});
      } finally {
        dispatchHandler.executeDispatch(frame,
            new Object[] {ensureHandler});
      }
    }

    @Override
    public final void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      throw new RuntimeException("This is not supported. Need to use a "
          + "different way, perhaps the DSL instead. but the "
          + "ValuePrimitiveNode interface doesn't work for two blocks.");
    }

    @Override
    public NodeCost getCost() {
      int dispatchChain = dispatchBody.lengthOfDispatchChain();
      if (dispatchChain == 0) {
        return NodeCost.UNINITIALIZED;
      } else if (dispatchChain == 1) {
        return NodeCost.MONOMORPHIC;
      } else if (dispatchChain <= AbstractDispatchNode.INLINE_CACHE_SIZE) {
        return NodeCost.POLYMORPHIC;
      } else {
        return NodeCost.MEGAMORPHIC;
      }
    }
  }


}
