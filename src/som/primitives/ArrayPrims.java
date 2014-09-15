package som.primitives;

import java.util.Arrays;

import som.interpreter.Invokable;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedValuePrimDispatchNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.primitives.BlockPrims.ValuePrimitiveNode;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public final class ArrayPrims {
  public abstract static class AtPrim extends BinarySideEffectFreeExpressionNode {
    @Specialization
    public final Object doSArray(final Object[] receiver, final long argument) {
      return receiver[(int) argument - 1];
    }
  }

  public abstract static class AtPutPrim extends TernaryExpressionNode {
    @Specialization
    public final Object doSArray(final Object[] receiver, final long index, final Object value) {
      receiver[(int) index - 1] = value;
      return value;
    }
  }

  public abstract static class NewPrim extends BinarySideEffectFreeExpressionNode {

    protected final boolean receiverIsArrayClass(final SClass receiver) {
      return receiver == Classes.arrayClass;
    }

    @Specialization(guards = "receiverIsArrayClass")
    public final Object[] doSClass(final SClass receiver, final long length) {
      Object[] result = new Object[(int) length];
      Arrays.fill(result, Nil.nilObject);
      return result;
    }
  }

  public abstract static class CopyPrim extends UnarySideEffectFreeExpressionNode {
    @Specialization
    public final Object[] doArray(final VirtualFrame frame, final Object[] receiver) {
      // TODO: should I set the owner differently?
      return receiver.clone();
    }
  }

  public abstract static class PutAllEagerOpt extends BinaryExpressionNode {
    public PutAllEagerOpt() { super(null); }

    protected boolean notABlock(final Object[] receiver, final Object value) {
      return !(value instanceof SBlock);
    }

    @Specialization(guards = "notABlock")
    public Object[] doPutValue(final Object[] receiver, final Object value) {
      Arrays.fill(receiver, /* SArray.FIRST_IDX */ 0, receiver.length, value);
      return receiver;
    }
  }

  public abstract static class DoIndexesPrim extends BinaryExpressionNode
      implements ValuePrimitiveNode {
    @Child private AbstractDispatchNode block;

    public DoIndexesPrim() {
      super(null);
      block = new UninitializedValuePrimDispatchNode();
    }

    @Specialization
    public final Object[] doArray(final VirtualFrame frame,
        final Object[] receiver, final SBlock block) {
      try {
        if (receiver.length > 0) {
          this.block.executeDispatch(frame, new Object[] {block, (long) 0 + 1}); // +1 because it is going to the smalltalk level
        }
        for (long i = 1; i < receiver.length; i++) {
          this.block.executeDispatch(frame, new Object[] {block, i + 1}); // +1 because it is going to the smalltalk level
        }
      } finally {
        if (CompilerDirectives.inInterpreter()) {
          reportLoopCount(receiver.length);
        }
      }
      return receiver;
    }

    protected final void reportLoopCount(final long count) {
      assert count >= 0;
      CompilerAsserts.neverPartOfCompilation("reportLoopCount");
      Node current = getParent();
      while (current != null && !(current instanceof RootNode)) {
        current = current.getParent();
      }
      if (current != null) {
        ((Invokable) current).propagateLoopCountThroughoutLexicalScope(count);
      }
    }

    @Override
    public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      block = insert(node);
    }
  }

  public abstract static class DoPrim extends BinaryExpressionNode
    implements ValuePrimitiveNode {
    @Child private AbstractDispatchNode block;

    public DoPrim() {
      super(null);
      block = new UninitializedValuePrimDispatchNode();
    }

    @Override
    public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      block = insert(node);
    }

    @Specialization
    public final Object[] doArray(final VirtualFrame frame,
        final Object[] receiver, final SBlock block) {
      try {
        if (receiver.length > 0) {
          this.block.executeDispatch(frame, new Object[] {block, receiver[0]});
        }
        for (int i = 1; i < receiver.length; i++) {
          this.block.executeDispatch(frame, new Object[] {block, receiver[i]});
        }
      } finally {
        if (CompilerDirectives.inInterpreter()) {
          reportLoopCount(receiver.length);
        }
      }
      return receiver;
    }

    protected final void reportLoopCount(final long count) {
      assert count > 0;
      CompilerAsserts.neverPartOfCompilation("reportLoopCount");
      Node current = getParent();
      while (current != null && !(current instanceof RootNode)) {
        current = current.getParent();
      }
      if (current != null) {
        ((Invokable) current).propagateLoopCountThroughoutLexicalScope(count);
      }
    }
  }
}
