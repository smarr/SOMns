package som.primitives.reflection;

import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class PerformInSuperclassPrim extends TernaryExpressionNode {
  @Specialization
  public final Object doSAbstractObject(final VirtualFrame frame,
      final SAbstractObject receiver, final SSymbol selector, final SClass  clazz) {
    CompilerAsserts.neverPartOfCompilation("PerformInSuperclassPrim");
    SInvokable invokable = clazz.lookupInvokable(selector);
    return invokable.invoke(receiver);
  }
}
