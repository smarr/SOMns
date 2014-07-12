package som.primitives;

import som.interpreter.Types;
import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;


public final class ObjectPrims {
  public abstract static class InstVarAtPrim extends BinarySideEffectFreeExpressionNode {
    public InstVarAtPrim(final boolean executesEnforced) { super(executesEnforced); }
    public InstVarAtPrim(final InstVarAtPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSObject(final SObject receiver, final long idx) {
      return receiver.getField(idx - 1);
    }
  }

  public abstract static class InstVarAtPutPrim extends TernaryExpressionNode {
    public InstVarAtPutPrim(final boolean executesEnforced) { super(executesEnforced); }
    public InstVarAtPutPrim(final InstVarAtPutPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSObject(final SObject receiver, final long idx, final SAbstractObject val) {
      receiver.setField(idx - 1, val);
      return val;
    }

    @Specialization
    public final Object doSObject(final SObject receiver, final long idx, final Object val) {
      receiver.setField(idx - 1, val);
      return val;
    }
  }

  public abstract static class InstVarNamedPrim extends BinarySideEffectFreeExpressionNode {
    public InstVarNamedPrim(final boolean executesEnforced) { super(executesEnforced); }
    public InstVarNamedPrim(final InstVarNamedPrim node) { this(node.executesEnforced); }

    @Specialization
    public final Object doSObject(final SObject receiver, final SSymbol fieldName) {
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
    private final Universe universe;
    public ClassPrim(final boolean executesEnforced) { super(executesEnforced); this.universe = Universe.current(); }
    public ClassPrim(final ClassPrim node) { this(node.executesEnforced); }

    @Specialization
    public final SClass doSAbstractObject(final SAbstractObject receiver) {
      return receiver.getSOMClass();
    }

    @Specialization
    public final SClass doObject(final Object receiver) {
      return Types.getClassOf(receiver, universe);
    }
  }
}
