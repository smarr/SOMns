package som.primitives;

import som.interpreter.Types;
import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.primitives.reflection.IndexDispatch;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class ObjectPrims {
  public abstract static class InstVarAtPrim extends BinarySideEffectFreeExpressionNode {

    @Child private IndexDispatch dispatch;

    public InstVarAtPrim(final boolean executesEnforced) {
      super(executesEnforced);
      dispatch = IndexDispatch.create();
    }
    public InstVarAtPrim(final InstVarAtPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSObject(final SObject receiver, final long idx) {
      return dispatch.executeDispatch(receiver, (int) idx - 1);
    }
  }

  public abstract static class InstVarAtPutPrim extends TernaryExpressionNode
      implements PreevaluatedExpression {
    @Child private IndexDispatch dispatch;

    public InstVarAtPutPrim(final boolean executesEnforced) {
      super(executesEnforced);
      dispatch = IndexDispatch.create();
    }
    public InstVarAtPutPrim(final InstVarAtPutPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSObject(final SObject receiver, final long idx, final Object val) {
      dispatch.executeDispatch(receiver, (int) idx - 1, val);
      return val;
    }

    @Override
    public Object doPreEvaluated(final VirtualFrame frame, final Object[] args) {
      assert args[0] instanceof SObject;
      assert args[1] instanceof Long;
      SObject rcvr = CompilerDirectives.unsafeCast(args[0], SObject.class, true, true);
      long idx     = CompilerDirectives.unsafeCast(args[1], long.class, true, true);
      return doSObject(rcvr, idx, args[2]);
    }
  }

  public abstract static class InstVarNamedPrim extends BinarySideEffectFreeExpressionNode {
    public InstVarNamedPrim(final boolean executesEnforced) { super(executesEnforced); }
    public InstVarNamedPrim(final InstVarNamedPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSObject(final SObject receiver, final SSymbol fieldName) {
      CompilerAsserts.neverPartOfCompilation();
      return receiver.getField(receiver.getFieldIndex(fieldName));
    }
  }

  public abstract static class HaltPrim extends UnaryExpressionNode {
    public HaltPrim(final boolean executesEnforced) { super(null, executesEnforced); }
    public HaltPrim(final HaltPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSAbstractObject(final Object receiver) {
      Universe.errorPrintln("BREAKPOINT");
      return receiver;
    }
  }

  public abstract static class ClassPrim extends UnarySideEffectFreeExpressionNode {
    public ClassPrim(final boolean executesEnforced) { super(executesEnforced); }
    public ClassPrim(final ClassPrim node) { this(node.executesEnforced); }

    @Specialization
    public final SClass doSAbstractObject(final SAbstractObject receiver) {
      return receiver.getSOMClass();
    }

    @Specialization
    public final SClass doObject(final Object receiver) {
      return Types.getClassOf(receiver);
    }
  }
}
