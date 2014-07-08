package som.primitives;

import som.interpreter.SArguments;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedValuePrimDispatchNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;


public abstract class BlockPrims {

  public interface ValuePrimitiveNode {
    void adoptNewDispatchListHead(final AbstractDispatchNode node);
  }

  public abstract static class RestartPrim extends UnaryExpressionNode {
    public RestartPrim(final boolean executesEnforced) { super(null, executesEnforced); }
    public RestartPrim(final RestartPrim node) { super(null, node.executesEnforced); }

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

    public ValueNonePrim(final boolean executesEnforced) {
      super(null, executesEnforced);
      dispatchNode = new UninitializedValuePrimDispatchNode();
    }
    public ValueNonePrim(final ValueNonePrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame, final SBlock receiver) {
      SObject domain = SArguments.domain(frame);
      return dispatchNode.executeDispatch(frame, domain, receiver.isEnforced(),
          new Object[] {receiver});
    }

    @Override
    public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
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

  public abstract static class ValueOnePrim extends BinaryExpressionNode
      implements ValuePrimitiveNode  {
    @Child private AbstractDispatchNode dispatchNode;

    public ValueOnePrim(final boolean executesEnforced) {
      super(null, executesEnforced);
      dispatchNode = new UninitializedValuePrimDispatchNode();
    }
    public ValueOnePrim(final ValueOnePrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame, final SBlock receiver,
        final Object arg) {
      SObject domain = SArguments.domain(frame);
      return dispatchNode.executeDispatch(frame, domain, receiver.isEnforced(),
          new Object[] {receiver, arg});
    }

    @Override
    public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
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

  public abstract static class ValueTwoPrim extends TernaryExpressionNode
      implements ValuePrimitiveNode {
    @Child private AbstractDispatchNode dispatchNode;

    public ValueTwoPrim(final boolean executesEnforced) {
      super(null, executesEnforced);
      dispatchNode = new UninitializedValuePrimDispatchNode();
    }
    public ValueTwoPrim(final ValueTwoPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object arg1, final Object arg2) {
      SObject domain = SArguments.domain(frame);
      return dispatchNode.executeDispatch(frame, domain, receiver.isEnforced(),
          new Object[] {receiver, arg1, arg2});
    }

    @Override
    public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
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

  public abstract static class ValueMorePrim extends QuaternaryExpressionNode {
    public ValueMorePrim(final boolean executesEnforced) { super(null, executesEnforced); }
    public ValueMorePrim(final ValueMorePrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSBlock(final VirtualFrame frame,
        final SBlock receiver, final Object firstArg, final Object secondArg,
        final Object thirdArg) {
      CompilerDirectives.transferToInterpreter();
      throw new RuntimeException("This should never be called, because SOM Blocks have max. 2 arguments.");
    }
  }
}
