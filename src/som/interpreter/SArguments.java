package som.interpreter;

import com.oracle.truffle.api.frame.Frame;

import som.primitives.SizeAndLengthPrim;
import som.primitives.arrays.AtPrim;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SImmutableArray;

import java.util.*;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import som.interpreter.nodes.ExpressionNode;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import tools.debugger.asyncstacktraces.ShadowStackEntry;
import tools.debugger.asyncstacktraces.ShadowStackEntryLoad;


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

  public static Object[] convertToArgumentArray(final Object[] args) {
    int argLength = args.length;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      argLength++;
    } else {
      return args;
    }

    Object[] array = Arrays.copyOf(args, argLength);
    array[argLength - 1] = SArguments.instantiateTopShadowStackEntry(null);
    return array;
  }

  public static Object[] allocateArgumentsArray(final ExpressionNode[] argumentNodes) {
    int argLength = argumentNodes.length;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      argLength++;
    }
    return new Object[argLength];
  }

  /**
   * Create a new array from an SArguments array that contains only the true
   * arguments and excludes the receiver. This is used for instance for
   * #doesNotUnderstand (#dnu)
   */
  public static SImmutableArray getArgumentsWithoutReceiver(final Object[] arguments) {
//    if (arguments.length == 1) {
//      return new SImmutableArray(0, Classes.valueArrayClass);
//    }
//
//    Object[] argsArr = getPlainArgumentWithoutReceiver(arguments);
//    return new SImmutableArray(argsArr, Classes.valueArrayClass);

    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      if (arguments.length == 2) {
        assert arguments[1] instanceof ShadowStackEntry;
        return new SImmutableArray(0, Classes.valueArrayClass);
      }
      Object[] argsArr = getPlainArgumentWithoutReceiver(arguments);
      return new SImmutableArray(argsArr, Classes.valueArrayClass);

    } else {
      if (arguments.length == 1) {
        return new SImmutableArray(0, Classes.valueArrayClass);
      }

      Object[] argsArr = getPlainArgumentWithoutReceiver(arguments);
      return new SImmutableArray(argsArr, Classes.valueArrayClass);
    }
  }

  /**
   * Create a new array and copy inside the arguments without the receiver.
   * Used for FFI calls and DNUs.
   */
  public static Object[] getPlainArgumentWithoutReceiver(final Object[] arguments) {
    int rcvrIdx = 0; // the code and magic numbers below are based on the following assumption
    assert RCVR_IDX == rcvrIdx;
    assert arguments.length >= 1; // <- that's the receiver
//    Object[] argsArr = new Object[arguments.length - 1];

    int argsSize = arguments.length - 1;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      argsSize--;
    }
    Object[] argsArr = new Object[argsSize];

    System.arraycopy(arguments, 1, argsArr, 0, argsArr.length);
    return argsArr;
  }

//  public static Object[] getPlainArgumentsWithReceiver(final Object receiver,
//      final SArray args, final SizeAndLengthPrim size, final AtPrim at) {
//    Object[] result = new Object[(int) (size.executeEvaluated(args) + 1)];
//    result[0] = receiver;
//    for (int i = 1; i < result.length; i++) {
//      result[i] = at.executeEvaluated(null, args, (long) i);
//    }
//    return result;
//  }

  public static Object[] getPlainArgumentsWithReceiver(final Object receiver,
                                                       final SArray args, final SizeAndLengthPrim size, final AtPrim at,
                                                       final ExpressionNode expression,
                                                       final ShadowStackEntryLoad entryLoad,
                                                       final VirtualFrame frame) {
    int argSize = (int) (size.executeEvaluated(args) + 1);
    int defaultArgSize = argSize;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      argSize++;
    }

    Object[] result = new Object[argSize];
    result[0] = receiver;
    for (int i = 1; i < defaultArgSize; i++) {
      result[i] = at.executeEvaluated(null, args, (long) i);
    }

    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      entryLoad.loadShadowStackEntry(result, expression, frame, false);
    }
    return result;
  }

  public static Object[] getPlainXArgumentsWithReceiver(final ExpressionNode expression,
                                                        final ShadowStackEntryLoad entryLoad,
                                                        final VirtualFrame frame,
                                                        final Object... rcvrAndArgs) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      Object[] arguments = new Object[rcvrAndArgs.length + 1];
      for (int i = 0; i < rcvrAndArgs.length; i++) {
        arguments[i] = rcvrAndArgs[i];
      }
      entryLoad.loadShadowStackEntry(arguments, expression, frame, false);
      return arguments;
    } else {
      return rcvrAndArgs;
    }
  }

  public static Object[] getPlainArguments(final Object[] args) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      Object[] newArgs = new Object[args.length + 1];
      for (int i = 0; i < args.length; i++) {
        newArgs[i] = args[i];
      }
      return newArgs;
    } else {
      return args;
    }
  }

  public static Object[] getPromiseCallbackArgumentArray(final SBlock callback) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      return new Object[] {callback, null, null};
    } else {
      return new Object[] {callback, null};
    }
  }

  public static void setShadowStackEntryWithCache(final Object[] arguments,
                                                  final Node expression,
                                                  final ShadowStackEntryLoad entryLoad,
                                                  final VirtualFrame frame,
                                                  final boolean async) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      entryLoad.loadShadowStackEntry(arguments, expression, frame, async);
    }
  }

  public static ShadowStackEntry instantiateTopShadowStackEntry(final Node expr) {
    return ShadowStackEntry.createTop(expr);
  }

  public static ShadowStackEntry instantiateShadowStackEntry(final ShadowStackEntry previous,
                                                             final Node expr, final boolean async) {
    CompilerAsserts.partialEvaluationConstant(async);
    if (async) {
      return ShadowStackEntry.createAtAsyncSend(previous, expr);
    } else {
      return ShadowStackEntry.create(previous, expr);
    }
  }

  public static ShadowStackEntry getShadowStackEntry(final VirtualFrame frame) {
    Object[] args = frame.getArguments();
    return getShadowStackEntry(args);
  }

  public static ShadowStackEntry getShadowStackEntry(final Object[] args) {
    Object maybeShadowStack = args[args.length - 1];
    if (maybeShadowStack instanceof ShadowStackEntry) {
      return (ShadowStackEntry) maybeShadowStack;
    }
    return null;
  }

  public static void setShadowStackEntry(final Object[] args, final ShadowStackEntry entry) {
    assert args[args.length - 1] == null : "Assume shadow stack entry is not already set.";
    args[args.length - 1] = entry;
  }

  public static int getLengthWithoutShadowStack(final Object[] arguments) {
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      return arguments.length - 1;
    } else {
      return arguments.length;
    }
  }

  //set the resolution of the promise for the registered callback or message sent to a promise
  public static void saveCausalEntryForPromise(Object previousPromiseStack, Object currentPromiseStack) {
      assert previousPromiseStack != null && previousPromiseStack instanceof ShadowStackEntry;
      assert currentPromiseStack != null && currentPromiseStack instanceof ShadowStackEntry;
      ((ShadowStackEntry) currentPromiseStack).setPreviousShadowStackEntry((ShadowStackEntry) previousPromiseStack);
  }

  private static Map<Long, ShadowStackEntry> previousPromiseInGroupByActor = new HashMap<>();

  public static void saveCausalEntryForPromiseGroup(Object previousPromiseStack, Object callbackPromiseStack, long actorId) {
    if (previousPromiseInGroupByActor != null && previousPromiseInGroupByActor.containsKey(actorId)) {
      ShadowStackEntry firstPromise = previousPromiseInGroupByActor.get(actorId).getPreviousShadowStackEntry();

      //group frames of the previous promise with the frames of the new promise, and then set the grouped stack entries for the callback
      ShadowStackEntry groupedFrames = groupFrames(firstPromise, (ShadowStackEntry) previousPromiseStack);
      saveCausalEntryForPromise(groupedFrames, callbackPromiseStack);

    } else {
      saveCausalEntryForPromise(previousPromiseStack, callbackPromiseStack);
      previousPromiseInGroupByActor.put(actorId, (ShadowStackEntry) callbackPromiseStack);
    }
  }

  //group non repeated frames
  private static ShadowStackEntry groupFrames(ShadowStackEntry firstPromiseStack, ShadowStackEntry secondPromiseStack) {
    List<ShadowStackEntry> listSecond = getAllEntries(secondPromiseStack, new ArrayList<>());
    List<ShadowStackEntry> listFirst = getAllEntries(firstPromiseStack, new ArrayList<>());

    ShadowStackEntry group = setNewEntryAtEqualSourceSection(firstPromiseStack, listFirst, listSecond);

    return group;
  }

  private static List<ShadowStackEntry> getAllEntries(ShadowStackEntry stackEntry, List<ShadowStackEntry> list) {
    if (stackEntry == null) {
      return list;
    } else {
      list.add(stackEntry);
      getAllEntries(stackEntry.getPreviousShadowStackEntry(), list);
    }
    return list;
  }

  //equal source section corresponds to the turn node, from there on the stack frames are the same for both promises stacks
  private static ShadowStackEntry setNewEntryAtEqualSourceSection(ShadowStackEntry stackEntryToAdd, List<ShadowStackEntry> listFirst, List<ShadowStackEntry> listSecond ) {
    for (ShadowStackEntry entrySecond: listSecond) {
      for (ShadowStackEntry entryFirst: listFirst) {
        boolean entryFirstNotNull = entryFirst.getPreviousShadowStackEntry()!= null && entryFirst.getPreviousShadowStackEntry().getExpression()!= null;
        boolean entrySecondNotNull = entrySecond.getPreviousShadowStackEntry()!= null && entrySecond.getPreviousShadowStackEntry().getExpression()!= null;
        if (entryFirstNotNull && entrySecondNotNull && entrySecond.getPreviousShadowStackEntry().getSourceSection().equals(entryFirst.getPreviousShadowStackEntry().getSourceSection())) {
          entrySecond.setPreviousShadowStackEntry(stackEntryToAdd);
          return listSecond.get(0); //return top entry with the update
        }
      }
    }
    return listSecond.get(0);//return top entry
  }
}
