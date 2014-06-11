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

import com.oracle.truffle.api.dsl.Specialization;


public final class ObjectPrims {
  public abstract static class InstVarAtPrim extends BinarySideEffectFreeExpressionNode {
    public InstVarAtPrim() { super(false); } /* TODO: enforced!!! */
    @Specialization
    public final Object doSObject(final SObject receiver, final long idx) {
      return receiver.getField(idx - 1);
    }
  }

  public abstract static class InstVarAtPutPrim extends TernaryExpressionNode {
    public InstVarAtPutPrim() { super(false); } /* TODO: enforced!!! */
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

  public abstract static class HaltPrim extends UnaryExpressionNode {
    public HaltPrim() { super(null, false); } /* TODO: enforced!!! */
    @Specialization
    public final SAbstractObject doSAbstractObject(final SAbstractObject receiver) {
      Universe.errorPrintln("BREAKPOINT");
      return receiver;
    }
  }

  public abstract static class ClassPrim extends UnarySideEffectFreeExpressionNode {
    private final Universe universe;
    public ClassPrim() { super(false); /* TODO: enforced!!! */ this.universe = Universe.current(); }

    @Specialization
    public final SClass doSAbstractObject(final SAbstractObject receiver) {
      return receiver.getSOMClass(universe);
    }

    @Specialization
    public final SClass doObject(final Object receiver) {
      return Types.getClassOf(receiver, universe);
    }
  }
}
