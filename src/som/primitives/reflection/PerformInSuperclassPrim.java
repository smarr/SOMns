package som.primitives.reflection;

import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;


@GenerateNodeFactory
public abstract class PerformInSuperclassPrim extends TernaryExpressionNode {
  @Child private IndirectCallNode call = Truffle.getRuntime().createIndirectCallNode();

  @Specialization
  public final Object doSAbstractObject(final VirtualFrame frame,
      final SAbstractObject receiver, final SSymbol selector, final SClass  clazz) {
    VM.needsToBeOptimized("PerformInSuperclassPrim");
    Dispatchable invokable = clazz.lookupMessage(selector, AccessModifier.PUBLIC);
    return call.call(frame, invokable.getCallTarget(), new Object[] {receiver});
  }
}
