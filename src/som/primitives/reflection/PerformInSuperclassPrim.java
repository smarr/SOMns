package som.primitives.reflection;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;


@GenerateNodeFactory
public abstract class PerformInSuperclassPrim extends TernaryExpressionNode {
  @Child private IndirectCallNode call = Truffle.getRuntime().createIndirectCallNode();

  public PerformInSuperclassPrim(final SourceSection source) {
    super(false, source);
  }

  @Specialization
  public final Object doSAbstractObject(final SAbstractObject receiver,
      final SSymbol selector, final SClass clazz) {
    VM.thisMethodNeedsToBeOptimized("PerformInSuperclassPrim");
    Dispatchable invokable = clazz.lookupMessage(selector, AccessModifier.PUBLIC);
    return invokable.invoke(call, new Object[] {receiver});
  }
}
