package som.primitives;

import java.util.Arrays;

import som.interpreter.Invokable;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedValuePrimDispatchNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.BlockPrims.ValuePrimitiveNode;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public final class ArrayPrims {
  public abstract static class AtPrim extends BinaryExpressionNode {

    @Specialization
    public final Object doSArray(final Object[] receiver, final long argument) {
      return SArray.get(receiver, argument - 1);
    }
  }

  public abstract static class AtPutPrim extends TernaryExpressionNode {
    @Specialization
    public final Object doSArray(final Object[] receiver,
        final long index, final Object value) {
      SArray.set(receiver, index - 1, value);
      return value;
    }
  }

  public abstract static class NewPrim extends BinaryExpressionNode {

    protected final boolean receiverIsArrayClass(final SClass receiver) {
      return receiver == Classes.arrayClass;
    }

    @Specialization(guards = "receiverIsArrayClass")
    public final Object[] doSClass(final SClass receiver, final long length) {
      return SArray.newSArray(length, Nil.nilObject);
    }
  }

  public abstract static class CopyPrim extends UnaryExpressionNode {
    @Specialization
    public final Object[] doArray(final VirtualFrame frame, final Object[] receiver) {
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
      Arrays.fill(receiver, SArray.FIRST_IDX, receiver.length, value);
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
        assert SArray.FIRST_IDX == 0;
        if (SArray.FIRST_IDX < receiver.length) {
          this.block.executeDispatch(frame, new Object[] {block, (long) SArray.FIRST_IDX + 1}); // +1 because it is going to the smalltalk level
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
        final Object[] arr, final SBlock block) {
      try {
        if (SArray.FIRST_IDX < arr.length) {
          this.block.executeDispatch(frame, new Object[] {block, SArray.get(arr, SArray.FIRST_IDX)});
        }
        for (long i = SArray.FIRST_IDX + 1; i < arr.length; i++) {
          this.block.executeDispatch(frame, new Object[] {block, SArray.get(arr, i)});
        }
      } finally {
        if (CompilerDirectives.inInterpreter()) {
          reportLoopCount(arr.length);
        }
      }
      return arr;
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
