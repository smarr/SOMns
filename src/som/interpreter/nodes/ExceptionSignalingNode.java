package som.interpreter.nodes;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.nodes.PreevaluatedExpression;
import som.vm.Symbols;
import som.vm.constants.KernelObj;
import som.vmobjects.SClass;
import som.vmobjects.SObject;


public class ExceptionSignalingNode extends Node {

  @Child protected ExpressionNode getExceptionClassNode;
  @Child protected ExpressionNode signalExceptionNode;
  protected final SObject         module;

  public static ExceptionSignalingNode createNotAValueExceptionSignalingNode(
      final SourceSection sourceSection) {
    return createKernelSignalWithExceptionNode("NotAValue", sourceSection);
  }

  public static ExceptionSignalingNode createArgumentErrorExceptionSignalingNode(
      final SourceSection sourceSection) {
    return createKernelSignalWithExceptionNode("ArgumentError", sourceSection);
  }

  public static ExceptionSignalingNode createKernelSignalWithExceptionNode(
      final String exceptionClassName, final SourceSection sourceSection) {
    return new ExceptionSignalingNode(exceptionClassName,
        "signalWith:", sourceSection, KernelObj.kernel);
  }

  public ExceptionSignalingNode(final String exceptionClassName,
      final String signallingMethodName, final SourceSection sourceSection,
      final SObject theModule) {
    super();
    getExceptionClassNode = MessageSendNode.createGeneric(
        Symbols.symbolFor(exceptionClassName), null,
        sourceSection);
    signalExceptionNode = MessageSendNode.createGeneric(
        Symbols.symbolFor(signallingMethodName),
        new ExpressionNode[] {getExceptionClassNode},
        sourceSection);
    module = theModule;
  }

  public Object execute(final Object... args) {
    SClass exceptionClass =
        (SClass) ((PreevaluatedExpression) getExceptionClassNode).doPreEvaluated(null,
            new Object[] {module});
    return ((PreevaluatedExpression) signalExceptionNode).doPreEvaluated(null,
        mergeObjectWithArray(exceptionClass, args));
  }

  public static Object[] mergeObjectWithArray(final Object o, final Object[] objects) {
    Object[] allArgs = new Object[objects.length + 1];
    allArgs[0] = o;
    for (int i = 0; i < objects.length; i++) {
      allArgs[i + 1] = objects[i];
    }
    return allArgs;
  }
}
