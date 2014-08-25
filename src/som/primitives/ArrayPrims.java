package som.primitives;

import static som.vmobjects.SDomain.getDomainForNewObjects;

import java.util.Arrays;

import som.interpreter.AbstractInvokable;
import som.interpreter.SArguments;
import som.interpreter.nodes.ArgumentReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.UninitializedValuePrimDispatchNode;
import som.interpreter.nodes.enforced.EnforcedPrim;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.primitives.BlockPrims.ValuePrimitiveNode;
import som.vm.Universe;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable.SPrimitive;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


public final class ArrayPrims {
  public abstract static class AtPrim extends BinarySideEffectFreeExpressionNode {
    public AtPrim(final boolean executesEnforced) { super(executesEnforced); }
    public AtPrim(final AtPrim node) { super(node.executesEnforced); }

    @Specialization
    public final Object doSArray(final Object[] receiver, final long argument) {
      return SArray.get(receiver, argument);
    }
  }

  public abstract static class AtPutPrim extends TernaryExpressionNode {
    public AtPutPrim(final boolean executesEnforced) { super(executesEnforced); }
    public AtPutPrim(final AtPutPrim node) { super(node.executesEnforced); }

    @Specialization
    public final Object doSArray(final Object[] receiver,
        final long index, final Object value) {
      SArray.set(receiver, index, value);
      return value;
    }
  }

  public abstract static class NewPrim extends BinarySideEffectFreeExpressionNode {
    public NewPrim(final boolean executesEnforced) { super(executesEnforced); }
    public NewPrim(final NewPrim node) { this(node.executesEnforced); }

    protected final boolean receiverIsArrayClass(final SClass receiver) {
      return receiver == Classes.arrayClass;
    }

    @Specialization(guards = "receiverIsArrayClass")
    public final Object[] doSClass(final VirtualFrame frame,
        final SClass receiver, final long length) {
      SObject domain = SArguments.domain(frame);
      return SArray.newSArray(length, Nil.nilObject,
          getDomainForNewObjects(domain));
    }
  }

  public abstract static class CopyPrim extends UnarySideEffectFreeExpressionNode {
    public CopyPrim(final boolean executesEnforced) { super(executesEnforced); }
    public CopyPrim(final CopyPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object[] doArray(final VirtualFrame frame, final Object[] receiver) {
      // TODO: should I set the owner differently?
      return receiver.clone();
    }
  }

  public abstract static class PutAllEagerOpt extends BinaryExpressionNode {
    public PutAllEagerOpt(final boolean executesEnforced) { super(null, executesEnforced); }
    public PutAllEagerOpt(final PutAllEagerOpt node) { this(node.executesEnforced); }

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

    public DoIndexesPrim(final boolean executesEnforced) {
      super(null, executesEnforced);
      block = new UninitializedValuePrimDispatchNode();
    }
    public DoIndexesPrim(final DoIndexesPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object[] doArray(final VirtualFrame frame,
        final Object[] receiver, final SBlock block) {
      SObject currentDomain = SArguments.domain(frame);
      try {
        if (SArray.FIRST_IDX < receiver.length) {
          this.block.executeDispatch(frame, currentDomain, executesEnforced,
              new Object[] {block, (long) SArray.FIRST_IDX});
        }
        for (long i = SArray.FIRST_IDX + 1; i < receiver.length; i++) {
          this.block.executeDispatch(frame, currentDomain, executesEnforced,
              new Object[] {block, i});
        }
      } finally {
        if (CompilerDirectives.inInterpreter()) {
          reportLoopCount(receiver.length - SArray.FIRST_IDX);
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
        ((AbstractInvokable) current).propagateLoopCountThroughoutLexicalScope(count);
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
    @Child private EnforcedPrim enforcedAt;

    public DoPrim(final boolean executesEnforced) {
      super(null, executesEnforced);
      block = new UninitializedValuePrimDispatchNode();

      if (executesEnforced) {
        enforcedAt = EnforcedPrim.create(new ExpressionNode[] {
                                          new ArgumentReadNode(0, true),
                                          new ArgumentReadNode(1, true)});
        enforcedAt.setPrimitive(
            (SPrimitive) Classes.arrayClass.lookupInvokable(
                Universe.current().symbolFor("at:")));
      }
    }
    public DoPrim(final DoPrim node) { this(node.executesEnforced); }

    @Override
    public void adoptNewDispatchListHead(final AbstractDispatchNode node) {
      block = insert(node);
    }

    @Specialization(guards = "nodeExecutesEnforced", order = 1)
    public final Object[] doArrayEnforced(final VirtualFrame frame,
        final Object[] receiver, final SBlock block) {
      SObject currentDomain = SArguments.domain(frame);
      try {
        if (SArray.FIRST_IDX < receiver.length) {
          this.block.executeDispatch(frame, currentDomain, executesEnforced,
              new Object[] {block,
              enforcedAt.executeEvaluated(
                  frame, receiver, new Object[] {SArray.FIRST_IDX})
          });
        }
        for (long i = SArray.FIRST_IDX + 1; i < receiver.length; i++) {
          this.block.executeDispatch(frame, currentDomain, executesEnforced,
              new Object[] {block, enforcedAt.executeEvaluated(
                  frame, receiver, new Object[] {i})});
        }
      } finally {
        if (CompilerDirectives.inInterpreter()) {
          reportLoopCount(receiver.length - SArray.FIRST_IDX);
        }
      }
      return receiver;
    }

    @Specialization(guards = "nodeExecutesUnenforced", order = 2)
    public final Object[] doArrayUnenforced(final VirtualFrame frame,
        final Object[] receiver, final SBlock block) {
      SObject currentDomain = SArguments.domain(frame);
      try {
        if (SArray.FIRST_IDX < receiver.length) {
          this.block.executeDispatch(frame, currentDomain, executesEnforced,
              new Object[] {block, SArray.get(receiver, SArray.FIRST_IDX)});
        }
        for (long i = SArray.FIRST_IDX + 1; i < receiver.length; i++) {
          this.block.executeDispatch(frame, currentDomain, executesEnforced,
              new Object[] {block, SArray.get(receiver, i)});
        }
      } finally {
        if (CompilerDirectives.inInterpreter()) {
          reportLoopCount(receiver.length - SArray.FIRST_IDX);
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
        ((AbstractInvokable) current).propagateLoopCountThroughoutLexicalScope(count);
      }
    }
  }
}
