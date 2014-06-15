package som.primitives.reflection;

import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public abstract class PerformWithArgumentsInSuperclassPrim extends QuaternaryExpressionNode {
  public PerformWithArgumentsInSuperclassPrim() { super(null); }

  @Specialization
  public final Object doSAbstractObject(final VirtualFrame frame,
      final SAbstractObject receiver, final SSymbol selector,
      final Object[] argArr, final SClass clazz) {
    CompilerAsserts.neverPartOfCompilation();
    SInvokable invokable = clazz.lookupInvokable(selector);
    return invokable.invoke(mergeReceiverWithArguments(receiver, argArr));
  }

  // TODO: remove duplicated code, also in symbol dispatch, ideally removing by optimizing this implementation...
  @ExplodeLoop
  private static Object[] mergeReceiverWithArguments(final Object receiver, final Object[] argsArray) {
    Object[] arguments = new Object[argsArray.length + 1];
    arguments[0] = receiver;
    for (int i = 0; i < argsArray.length; i++) {
      arguments[i + 1] = argsArray[i];
    }
    return arguments;
  }
}
