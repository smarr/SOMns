package somns.primitives.reflection;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;

import somns.VM;
import somns.compiler.AccessModifier;
import somns.interpreter.nodes.dispatch.Dispatchable;
import somns.interpreter.nodes.nary.TernaryExpressionNode;
import somns.vmobjects.SAbstractObject;
import somns.vmobjects.SClass;
import somns.vmobjects.SSymbol;


@GenerateNodeFactory
public abstract class PerformInSuperclassPrim extends TernaryExpressionNode {
  @Child private IndirectCallNode call = Truffle.getRuntime().createIndirectCallNode();

  @Specialization
  public final Object doSAbstractObject(final SAbstractObject receiver,
      final SSymbol selector, final SClass clazz) {
    VM.thisMethodNeedsToBeOptimized("PerformInSuperclassPrim");
    Dispatchable invokable = clazz.lookupMessage(selector, AccessModifier.PUBLIC);
    return invokable.invoke(call, new Object[] {receiver});
  }
}
