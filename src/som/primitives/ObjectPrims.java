package som.primitives;

import som.interpreter.Types;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.BinaryExpressionNode.BinarySideEffectFreeExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class ObjectPrims {
  public abstract static class PerformPrim extends BinaryExpressionNode {
    private final Universe universe;
    public PerformPrim() { this.universe = Universe.current(); }

    @Specialization
    public final Object doObject(final VirtualFrame frame, final Object receiver, final SSymbol selector) {
      SInvokable invokable = Types.getClassOf(receiver, universe).lookupInvokable(selector);
      return invokable.invoke(receiver);
    }
  }

  public abstract static class PerformInSuperclassPrim extends TernaryExpressionNode {
    @Specialization
    public final Object doSAbstractObject(final VirtualFrame frame,
        final SAbstractObject receiver, final SSymbol selector, final SClass  clazz) {
      SInvokable invokable = clazz.lookupInvokable(selector);
      return invokable.invoke(receiver);
    }
  }

  public abstract static class PerformWithArgumentsPrim extends TernaryExpressionNode {
    private final Universe universe;
    public PerformWithArgumentsPrim() { this.universe = Universe.current(); }

    @Specialization
    public final Object doObject(final VirtualFrame frame,
        final Object receiver, final SSymbol selector, final Object[]  argsArr) {
      SInvokable invokable = Types.getClassOf(receiver, universe).lookupInvokable(selector);

      // need to unwrap argsArr and create a new Object array including the
      // receiver
      Object[] args = new Object[argsArr.length + 1];
      args[0] = receiver;
      System.arraycopy(argsArr, 0, args, 1, argsArr.length);

      return invokable.invoke(args);
    }
  }

  public abstract static class InstVarAtPrim extends BinarySideEffectFreeExpressionNode {
    @Specialization
    public final Object doSObject(final SObject receiver, final int idx) {
      return receiver.getField(idx - 1);
    }
  }

  public abstract static class InstVarAtPutPrim extends TernaryExpressionNode {
    @Specialization
    public final Object doSObject(final SObject receiver, final int idx, final SAbstractObject val) {
      receiver.setField(idx - 1, val);
      return val;
    }

    @Specialization
    public final Object doSObject(final SObject receiver, final int idx, final Object val) {
      receiver.setField(idx - 1, val);
      return val;
    }
  }

  public abstract static class HaltPrim extends UnaryExpressionNode {
    @Specialization
    public final SAbstractObject doSAbstractObject(final SAbstractObject receiver) {
      Universe.errorPrintln("BREAKPOINT");
      return receiver;
    }
  }

  public abstract static class ClassPrim extends UnarySideEffectFreeExpressionNode {
    private final Universe universe;
    public ClassPrim() { this.universe = Universe.current(); }

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
