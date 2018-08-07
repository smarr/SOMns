package som.interpreter;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.nodes.ExpressionNode;
import som.primitives.SizeAndLengthPrim;
import som.primitives.arrays.AtPrim;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SImmutableArray;
import tools.asyncstacktraces.ShadowStackEntry;


public final class SArguments {

  public static final int RCVR_IDX = 0;

  private static Object[] args(final Frame frame) {
    return frame.getArguments();
  }

  public static Object arg(final Frame frame, final int index) {
    return args(frame)[index];
  }

  public static Object rcvr(final Frame frame) {
    return arg(frame, RCVR_IDX);
  }

  /**
   * Create a new array from an SArguments array that contains only the true
   * arguments and excludes the receiver. This is used for instance for
   * #doesNotUnderstand (#dnu)
   */
  public static SImmutableArray getArgumentsWithoutReceiver(final Object[] arguments) {
    if (arguments.length == 1) {
      return new SImmutableArray(0, Classes.valueArrayClass);
    }

    Object[] argsArr = getPlainArgumentWithoutReceiver(arguments);
    return new SImmutableArray(argsArr, Classes.valueArrayClass);
  }

  public static Object[] getPlainArgumentWithoutReceiver(final Object[] arguments) {
    int rcvrIdx = 0; // the code and magic numbers below are based on the following assumption
    assert RCVR_IDX == rcvrIdx;
    assert arguments.length >= 1; // <- that's the receiver
    Object[] argsArr = new Object[arguments.length - 1];

    System.arraycopy(arguments, 1, argsArr, 0, argsArr.length);
    return argsArr;
  }

  public static Object[] allocateArgumentsArray(final ExpressionNode[] argumentNodes) {
    int argLength = argumentNodes.length;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      argLength++;
    }
    return new Object[argLength];
  }

  public static Object[] getPlainArgumentsWithReceiver(final Object receiver,
      final SArray args, final SizeAndLengthPrim size, final AtPrim at) {
    Object[] result = new Object[(int) (size.executeEvaluated(args) + 1)];
    result[0] = receiver;
    for (int i = 1; i < result.length; i++) {
      result[i] = at.executeEvaluated(null, args, (long) i);
    }
    return result;
  }

  /**
   *
   * @param expression Expression is typically a send
   */
  public static void setShadowStack(final Object[] arguments, final ExpressionNode expression,
      final VirtualFrame frame) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      arguments[arguments.length - 1] =
          new ShadowStackEntry(getShadowStackEntry(frame), expression);
    }
  }

  public static ShadowStackEntry getShadowStackEntry(final VirtualFrame frame) {
    Object[] args = frame.getArguments();
    Object maybeShadowStack = args[args.length - 1];
    if (maybeShadowStack instanceof ShadowStackEntry) {
      return (ShadowStackEntry) maybeShadowStack;
    }
    return null;
    // if (maybeShadowStack == null) {
    // return null;
    // }
    // return (ShadowStackEntry) maybeShadowStack;
  }
}
