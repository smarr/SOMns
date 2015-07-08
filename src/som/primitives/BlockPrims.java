package som.primitives;

import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedValuePrimDispatchNode;
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
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.NodeCost;


public abstract class BlockPrims {

  public interface ValuePrimitiveNode {
    void adoptNewDispatchListHead(final AbstractDispatchNode node);
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
  @Primitive("blockValue:")
  public abstract static class ValueNonePrim extends UnaryExpressionNode
      implements ValuePrimitiveNode {
    @Child private AbstractDispatchNode dispatchNode;

    public ValueNonePrim() {
      super(null);
      dispatchNode = new UninitializedValuePrimDispatchNode();
    }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame, final SBlock receiver) {
      return dispatchNode.executeDispatch(frame, new Object[] {receiver});
    }

    @Specialization
    public final boolean doBoolean(final boolean receiver) {
      return receiver;
    }

    @Override
    public final void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      dispatchNode = insert(node);
    }

    @Override
    public NodeCost getCost() {
      int dispatchChain = dispatchNode.lengthOfDispatchChain();
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
  @Primitive("blockValue:with:")
  public abstract static class ValueOnePrim extends BinaryExpressionNode {

    protected final DirectCallNode createDirectCallNode(final SBlock receiver) {
      assert null != receiver.getMethod().getCallTarget();
      return Truffle.getRuntime().createDirectCallNode(
          receiver.getMethod().getCallTarget());
    }

    @Specialization(guards = "cached == receiver.getMethod()", limit = "6")
    public final Object doCachedBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg,
        @Cached("createDirectCallNode(receiver)") final DirectCallNode call,
        @Cached("receiver.getMethod()") final SInvokable cached) {
      return call.call(frame, new Object[] {receiver, arg});
    }

    protected final IndirectCallNode create() {
      return Truffle.getRuntime().createIndirectCallNode();
    }

    @Specialization(contains = "doCachedBlock")
    public final Object doGeneric(final VirtualFrame frame,
        final SBlock receiver, final Object arg,
        @Cached("create()") final IndirectCallNode call) {
      return receiver.getMethod().invoke(frame, call, new Object[] {receiver, arg});
    }
  }

  @GenerateNodeFactory
  @Primitive("blockValue:with:with:")
  public abstract static class ValueTwoPrim extends TernaryExpressionNode
      implements ValuePrimitiveNode {
    @Child private AbstractDispatchNode dispatchNode;

    public ValueTwoPrim() {
      super(null);
      dispatchNode = new UninitializedValuePrimDispatchNode();
    }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2) {
      return dispatchNode.executeDispatch(frame, new Object[] {receiver, arg1, arg2});
    }

    @Override
    public final void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      dispatchNode = insert(node);
    }

    @Override
    public NodeCost getCost() {
      int dispatchChain = dispatchNode.lengthOfDispatchChain();
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
