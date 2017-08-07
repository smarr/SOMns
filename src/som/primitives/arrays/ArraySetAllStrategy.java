package som.primitives.arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import som.VM;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.primitives.ObjectPrims.IsValue;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.KernelObj;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;


public final class ArraySetAllStrategy {

  public static void evalBlockForRemaining(final SBlock block,
      final long length, final Object[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = blockDispatch.executeDispatch(new Object[] {block});
    }
  }

  @TruffleBoundary
  private static Object signalNotAValue() {
    // TODO: this is a duplicated from IsValueCheckNode
    // TODO: don't think this is a complete solution, we need to do something else here
    // perhaps write the node, and then also use a send node...
    CompilerDirectives.transferToInterpreter();
    VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");

    // the value object was not constructed properly.
    SInvokable disp = (SInvokable) KernelObj.kernel.getSOMClass().lookupPrivate(
        Symbols.symbolFor("signalNotAValueWith:"),
        KernelObj.kernel.getSOMClass().getMixinDefinition().getMixinId());
    return disp.invoke(new Object[] {KernelObj.kernel, Classes.valueArrayClass});
  }

  public static void evalBlockWithArgForRemaining(final SBlock block,
      final long length, final Object[] storage,
      final BlockDispatchNode blockDispatch, final Object first, final IsValue isValue) {
    if (!isValue.executeEvaluated(first)) {
      signalNotAValue();
    }
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      Object result = blockDispatch.executeDispatch(new Object[] {block, (long) i + 1});
      if (!isValue.executeEvaluated(result)) {
        signalNotAValue();
      } else {
        storage[i] = result;
      }
    }
  }

  public static void evalBlockForRemaining(final SBlock block,
      final long length, final long[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (long) blockDispatch.executeDispatch(new Object[] {block});
    }
  }

  public static void evalBlockForRemaining(final SBlock block,
      final long length, final double[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (double) blockDispatch.executeDispatch(new Object[] {block});
    }
  }

  public static void evalBlockForRemaining(final SBlock block,
      final long length, final boolean[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (boolean) blockDispatch.executeDispatch(new Object[] {block});
    }
  }

  public static void evalBlockWithArgForRemaining(final SBlock block,
      final long length, final long[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (long) blockDispatch.executeDispatch(
          new Object[] {block, (long) i + 1});
    }
  }

  public static void evalBlockWithArgForRemaining(final SBlock block,
      final long length, final double[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (double) blockDispatch.executeDispatch(
          new Object[] {block, (long) i + 1});
    }
  }

  public static void evalBlockWithArgForRemaining(final SBlock block,
      final long length, final boolean[] storage,
      final BlockDispatchNode blockDispatch) {
    for (int i = SArray.FIRST_IDX + 1; i < length; i++) {
      storage[i] = (boolean) blockDispatch.executeDispatch(
          new Object[] {block, (long) i + 1});
    }
  }

  @ExplodeLoop
  public static Object evalForRemaining(final VirtualFrame frame,
      final ExpressionNode[] exprs, final long[] storage, final int next) {
    for (int i = next; i < exprs.length; i++) {
      try {
        storage[i] = exprs[i].executeLong(frame);
      } catch (UnexpectedResultException e) {
        Object[] newStorage = new Object[exprs.length];
        for (int j = 0; j < i; j += 1) {
          newStorage[j] = storage[j];
        }
        newStorage[i] = e.getResult();
        return evalForRemaining(frame, exprs, newStorage, i + 1);
      }
    }
    return storage;
  }

  @ExplodeLoop
  public static Object evalForRemaining(final VirtualFrame frame,
      final ExpressionNode[] exprs, final boolean[] storage, final int next) {
    for (int i = next; i < exprs.length; i++) {
      try {
        storage[i] = exprs[i].executeBoolean(frame);
      } catch (UnexpectedResultException e) {
        Object[] newStorage = new Object[exprs.length];
        for (int j = 0; j < i; j += 1) {
          newStorage[j] = storage[j];
        }
        newStorage[i] = e.getResult();
        return evalForRemaining(frame, exprs, newStorage, i + 1);
      }
    }
    return storage;
  }

  @ExplodeLoop
  public static Object evalForRemaining(final VirtualFrame frame,
      final ExpressionNode[] exprs, final double[] storage, final int next) {
    for (int i = next; i < exprs.length; i++) {
      try {
        storage[i] = exprs[i].executeDouble(frame);
      } catch (UnexpectedResultException e) {
        Object[] newStorage = new Object[exprs.length];
        for (int j = 0; j < i; j += 1) {
          newStorage[j] = storage[j];
        }
        newStorage[i] = e.getResult();
        return evalForRemaining(frame, exprs, newStorage, i + 1);
      }
    }
    return storage;
  }

  @ExplodeLoop
  public static Object evalForRemaining(final VirtualFrame frame,
      final ExpressionNode[] exprs, final Object[] storage, final int next) {
    for (int i = next; i < exprs.length; i++) {
      storage[i] = exprs[i].executeGeneric(frame);
    }
    return storage;
  }

  @ExplodeLoop
  public static Object evalForRemainingNils(final VirtualFrame frame,
      final ExpressionNode[] exprs, final int next) {
    for (int i = next; i < exprs.length; i++) {
      Object result = exprs[i].executeGeneric(frame);
      if (result != Nil.nilObject) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        // TODO: not optimized for partially empty literals,
        // changes immediately to object storage
        Object[] newStorage = new Object[exprs.length];
        for (int j = 0; j < i; j += 1) {
          newStorage[j] = Nil.nilObject;
        }
        newStorage[i] = result;
        return evalForRemaining(frame, exprs, newStorage, i + 1);
      }
    }
    return exprs.length;
  }

  public static Object evaluateFirstDetermineStorageAndEvaluateRest(
      final SBlock blockNoArg, final long length,
      final BlockDispatchNode blockDispatch) {
    // TODO: this version does not handle the case that a subsequent value is
    // not of the expected type...
    Object result = blockDispatch.executeDispatch(new Object[] {blockNoArg});
    if (result instanceof Long) {
      long[] newStorage = new long[(int) length];
      newStorage[0] = (long) result;
      evalBlockForRemaining(blockNoArg, length, newStorage, blockDispatch);
      return newStorage;
    } else if (result instanceof Double) {
      double[] newStorage = new double[(int) length];
      newStorage[0] = (double) result;
      evalBlockForRemaining(blockNoArg, length, newStorage, blockDispatch);
      return newStorage;
    } else if (result instanceof Boolean) {
      boolean[] newStorage = new boolean[(int) length];
      newStorage[0] = (boolean) result;
      evalBlockForRemaining(blockNoArg, length, newStorage, blockDispatch);
      return newStorage;
    } else {
      Object[] newStorage = new Object[(int) length];
      newStorage[0] = result;
      evalBlockForRemaining(blockNoArg, length, newStorage, blockDispatch);
      return newStorage;
    }
  }

  public static Object evaluateFirstDetermineStorageAndEvaluateRest(
      final SBlock blockWithArg, final long length,
      final BlockDispatchNode blockDispatch, final IsValue isValue) {
    // TODO: this version does not handle the case that a subsequent value is
    // not of the expected type...
    Object result = blockDispatch.executeDispatch(new Object[] {blockWithArg, (long) 1});

    if (result instanceof Long) {
      long[] newStorage = new long[(int) length];
      newStorage[0] = (long) result;
      evalBlockWithArgForRemaining(blockWithArg, length, newStorage, blockDispatch);
      return newStorage;
    } else if (result instanceof Double) {
      double[] newStorage = new double[(int) length];
      newStorage[0] = (double) result;
      evalBlockWithArgForRemaining(blockWithArg, length, newStorage, blockDispatch);
      return newStorage;
    } else if (result instanceof Boolean) {
      boolean[] newStorage = new boolean[(int) length];
      newStorage[0] = (boolean) result;
      evalBlockWithArgForRemaining(blockWithArg, length, newStorage, blockDispatch);
      return newStorage;
    } else {
      Object[] newStorage = new Object[(int) length];
      evalBlockWithArgForRemaining(blockWithArg, length, newStorage, blockDispatch, result,
          isValue);
      return newStorage;
    }
  }

  public static Object evaluateFirstDetermineStorageAndEvaluateRest(
      final VirtualFrame frame, final ExpressionNode[] exprs) {
    Object result = exprs[0].executeGeneric(frame);
    if (result == Nil.nilObject) {
      return evalForRemainingNils(frame, exprs, SArray.FIRST_IDX + 1);
    } else if (result instanceof Long) {
      long[] newStorage = new long[exprs.length];
      newStorage[0] = (long) result;
      return evalForRemaining(frame, exprs, newStorage, SArray.FIRST_IDX + 1);
    } else if (result instanceof Double) {
      double[] newStorage = new double[exprs.length];
      newStorage[0] = (double) result;
      return evalForRemaining(frame, exprs, newStorage, SArray.FIRST_IDX + 1);
    } else if (result instanceof Boolean) {
      boolean[] newStorage = new boolean[exprs.length];
      newStorage[0] = (boolean) result;
      return evalForRemaining(frame, exprs, newStorage, SArray.FIRST_IDX + 1);
    } else {
      Object[] newStorage = new Object[exprs.length];
      newStorage[0] = result;
      return evalForRemaining(frame, exprs, newStorage, SArray.FIRST_IDX + 1);
    }
  }
}
