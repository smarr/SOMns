package somns.primitives.reflection;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;

import somns.VM;
import somns.compiler.AccessModifier;
import somns.interpreter.nodes.dispatch.Dispatchable;
import somns.interpreter.nodes.nary.QuaternaryExpressionNode;
import somns.vmobjects.SClass;
import somns.vmobjects.SSymbol;


@GenerateNodeFactory
public abstract class PerformWithArgumentsInSuperclassPrim extends QuaternaryExpressionNode {
  @Child private IndirectCallNode call = Truffle.getRuntime().createIndirectCallNode();

  @Specialization
  public final Object doSAbstractObject(final Object receiver, final SSymbol selector,
      final Object[] argArr, final SClass clazz) {
    VM.thisMethodNeedsToBeOptimized(
        "PerformWithArgumentsInSuperclassPrim.doSAbstractObject()");
    Dispatchable invokable = clazz.lookupMessage(selector, AccessModifier.PUBLIC);
    return invokable.invoke(call, mergeReceiverWithArguments(receiver, argArr));
  }

  // TODO: remove duplicated code, also in symbol dispatch, ideally removing by optimizing this
  // implementation...
  @ExplodeLoop
  private static Object[] mergeReceiverWithArguments(final Object receiver,
      final Object[] argsArray) {
    Object[] arguments = new Object[argsArray.length + 1];
    arguments[0] = receiver;
    for (int i = 0; i < argsArray.length; i++) {
      arguments[i + 1] = argsArray[i];
    }
    return arguments;
  }
}
