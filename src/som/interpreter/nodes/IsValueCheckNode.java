package som.interpreter.nodes;

import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.ObjectPrims.IsValue;
import som.vm.Symbols;
import som.vm.constants.KernelObj;
import som.vmobjects.SObject.SImmutableObject;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class IsValueCheckNode extends UnaryExpressionNode {

  public static IsValueCheckNode create(final ExpressionNode self) {
    return new UninitializedNode(self);
  }

  @Child protected ExpressionNode self;

  protected IsValueCheckNode(final ExpressionNode self) {
    this.self = self;
  }

  private static final class UninitializedNode extends IsValueCheckNode {
    public UninitializedNode(final ExpressionNode self) {
      super(self);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      specialize(frame, receiver);
      return receiver;
    }

    private void specialize(final VirtualFrame frame, final Object receiver) {
      if (!(receiver instanceof SImmutableObject)) {
        // can remove ourselves, this node is only used in initializers,
        // which are by definition monomorphic
        replace(self);
        return;
      }

      SImmutableObject rcvr = (SImmutableObject) receiver;
      // this is initialized to true if the class is declared as value
      if (!rcvr.isValue()) {
        replace(self);
        return;
      }

      replace(new CheckNode(self)).executeEvaluated(frame, receiver);
    }
  }

  private static final class CheckNode extends IsValueCheckNode {
    public CheckNode(final ExpressionNode self) {
      super(self);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver) {
      SImmutableObject rcvr = (SImmutableObject) receiver;

      boolean allFieldsContainValues = allFieldsContainValues(rcvr);
      if (allFieldsContainValues) {
        return rcvr;
      }

      CompilerAsserts.neverPartOfCompilation("Should be optimized or on slowpath");

      // the value object was not constructed properly.
      Dispatchable disp = KernelObj.kernel.getSOMClass().lookupPrivate(
          Symbols.symbolFor("signalNotAValueWith:"),
          KernelObj.kernel.getSOMClass().getClassDefinition().getClassId());
      return disp.invoke(KernelObj.kernel, rcvr.getSOMClass());
    }

    private boolean allFieldsContainValues(final SImmutableObject rcvr) {
      CompilerAsserts.neverPartOfCompilation("Should be optimized or on slowpath");

      if (rcvr.field1 == null) {
        return true;
      }

      boolean result = IsValue.isObjectValue(rcvr.field1);
      if (rcvr.field2 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field2);
      if (rcvr.field3 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field3);
      if (rcvr.field4 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field4);
      if (rcvr.field5 == null || !result) {
        return result;
      }

      result = result && IsValue.isObjectValue(rcvr.field5);
      if (rcvr.getExtensionObjFields() == null || !result) {
        return result;
      }

      Object[] ext = rcvr.getExtensionObjFields();
      for (Object o : ext) {
        if (!IsValue.isObjectValue(o)) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public final Object executeGeneric(final VirtualFrame frame) {
    return executeEvaluated(frame, self.executeGeneric(frame));
  }
}
