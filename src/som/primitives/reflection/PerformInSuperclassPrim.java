package som.primitives.reflection;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PerformInSuperclassPrim extends TernaryExpressionNode {
  public PerformInSuperclassPrim() { super(false); } /* TODO: enforced!!! */

  @Specialization
  public final Object doSAbstractObject(final VirtualFrame frame,
      final SAbstractObject receiver, final SSymbol selector, final SClass  clazz) {
    CompilerAsserts.neverPartOfCompilation();
    SInvokable invokable = clazz.lookupInvokable(selector);
    SObject domain = SArguments.domain(frame);
    boolean enforced = SArguments.enforced(frame);
    return invokable.invoke(domain, enforced, receiver);
  }

  public abstract static class PerformEnforcedInSuperclassPrim extends TernaryExpressionNode {
    public PerformEnforcedInSuperclassPrim() { super(false); } /* TODO: enforced!!! */

    @Specialization
    public final Object doSAbstractObject(final VirtualFrame frame,
        final SAbstractObject receiver, final SSymbol selector, final SClass  clazz) {
      CompilerAsserts.neverPartOfCompilation();
      SInvokable invokable = clazz.lookupInvokable(selector);
      SObject domain = SArguments.domain(frame);
      return invokable.invoke(domain, true, receiver);  // make sure it is enforced!
    }
  }
}
