package som.primitives.arrays;

import som.VM;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.primitives.ObjectPrims.IsValue;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.KernelObj;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class ArraySetAllStrategy {

  public static void evalBlockForRemaining(final VirtualFrame frame,
      final SBlock block, final long length, final Object[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = blockDispatch.executeDispatch(frame, new Object[] {block});
    }
  }

  @TruffleBoundary
  private static Object signalNotAValue() {
    // TODO: this is a duplicated from IsValueCheckNode
    //TODO: don't think this is a complete solution, we need to do something else here
    //       perhaps write the node, and then also use a send node...
    CompilerDirectives.transferToInterpreter();
    VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");

    // the value object was not constructed properly.
    Dispatchable disp = KernelObj.kernel.getSOMClass().lookupPrivate(
        Symbols.symbolFor("signalNotAValueWith:"),
        KernelObj.kernel.getSOMClass().getMixinDefinition().getMixinId());
    return disp.invoke(KernelObj.kernel, Classes.valueArrayClass);
  }

  public static void evalBlockWithArgForRemaining(final VirtualFrame frame,
      final SBlock block, final long length, final Object[] storage,
      final BlockDispatchNode blockDispatch, final Object first, final IsValue isValue) {
    if (!isValue.executeEvaluated(first)) {
      signalNotAValue();
    }
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      Object result = blockDispatch.executeDispatch(frame, new Object[] {block, (long) i + 1});
      if (!isValue.executeEvaluated(result)) {
        signalNotAValue();
      } else {
        storage[i] = result;
      }
    }
  }

  public static void evalBlockForRemaining(final VirtualFrame frame,
      final SBlock block, final long length, final long[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (long) blockDispatch.executeDispatch(frame, new Object[] {block});
    }
  }

  public static void evalBlockForRemaining(final VirtualFrame frame,
      final SBlock block, final long length, final double[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (double) blockDispatch.executeDispatch(frame, new Object[] {block});
    }
  }

  public static void evalBlockForRemaining(final VirtualFrame frame,
      final SBlock block, final long length, final boolean[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (boolean) blockDispatch.executeDispatch(frame, new Object[] {block});
    }
  }

  public static void evalBlockWithArgForRemaining(final VirtualFrame frame,
      final SBlock block, final long length, final long[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (long) blockDispatch.executeDispatch(frame, new Object[] {block, (long) i + 1});
    }
  }

  public static void evalBlockWithArgForRemaining(final VirtualFrame frame,
      final SBlock block, final long length, final double[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (double) blockDispatch.executeDispatch(frame, new Object[] {block, (long) i + 1});
    }
  }

  public static void evalBlockWithArgForRemaining(final VirtualFrame frame,
      final SBlock block, final long length, final boolean[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (boolean) blockDispatch.executeDispatch(frame, new Object[] {block, (long) i + 1});
    }
  }

  public static Object evaluateFirstDetermineStorageAndEvaluateRest(
      final VirtualFrame frame, final SBlock blockNoArg, final long length,
      final BlockDispatchNode blockDispatch) {
    // TODO: this version does not handle the case that a subsequent value is
    //       not of the expected type...
    Object result = blockDispatch.executeDispatch(frame, new Object[] {blockNoArg});
    if (result instanceof Long) {
      long[] newStorage = new long[(int) length];
      newStorage[0] = (long) result;
      evalBlockForRemaining(frame, blockNoArg, length, newStorage, blockDispatch);
      return newStorage;
    } else if (result instanceof Double) {
      double[] newStorage = new double[(int) length];
      newStorage[0] = (double) result;
      evalBlockForRemaining(frame, blockNoArg, length, newStorage, blockDispatch);
      return newStorage;
    } else if (result instanceof Boolean) {
      boolean[] newStorage = new boolean[(int) length];
      newStorage[0] = (boolean) result;
      evalBlockForRemaining(frame, blockNoArg, length, newStorage, blockDispatch);
      return newStorage;
    } else {
      Object[] newStorage = new Object[(int) length];
      newStorage[0] = result;
      evalBlockForRemaining(frame, blockNoArg, length, newStorage, blockDispatch);
      return newStorage;
    }
  }

  public static Object evaluateFirstDetermineStorageAndEvaluateRest(
      final VirtualFrame frame, final SBlock blockWithArg, final long length,
      final BlockDispatchNode blockDispatch, final IsValue isValue) {
    // TODO: this version does not handle the case that a subsequent value is
    //       not of the expected type...
    Object result = blockDispatch.executeDispatch(frame, new Object[] {blockWithArg, (long) 1});

    if (result instanceof Long) {
      long[] newStorage = new long[(int) length];
      newStorage[0] = (long) result;
      evalBlockWithArgForRemaining(frame, blockWithArg, length, newStorage, blockDispatch);
      return newStorage;
    } else if (result instanceof Double) {
      double[] newStorage = new double[(int) length];
      newStorage[0] = (double) result;
      evalBlockWithArgForRemaining(frame, blockWithArg, length, newStorage, blockDispatch);
      return newStorage;
    } else if (result instanceof Boolean) {
      boolean[] newStorage = new boolean[(int) length];
      newStorage[0] = (boolean) result;
      evalBlockWithArgForRemaining(frame, blockWithArg, length, newStorage, blockDispatch);
      return newStorage;
    } else {
      Object[] newStorage = new Object[(int) length];
      evalBlockWithArgForRemaining(frame, blockWithArg, length, newStorage, blockDispatch, result, isValue);
      return newStorage;
    }
  }
}
