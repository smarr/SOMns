package som.primitives.arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.specialized.SomLoop;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.PartiallyEmptyArray;
import som.vmobjects.SBlock;


@GenerateNodeFactory
@Primitive(selector = "do:", receiverType = SArray.class, disabled = true)
public abstract class DoPrim extends BinaryComplexOperation {
  @Child private BlockDispatchNode block = BlockDispatchNodeGen.create();

  // TODO: tag properly, it is a loop and an access

  private void execBlock(final SBlock block, final Object arg) {
    this.block.executeDispatch(new Object[] {block, arg});
  }

  @Specialization(guards = "arr.isEmptyType()")
  public final SArray doEmptyArray(final SArray arr, final SBlock block) {
    int length = arr.getEmptyStorage();
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(block, Nil.nilObject);
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(block, Nil.nilObject);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Specialization(guards = "arr.isPartiallyEmptyType()")
  public final SArray doPartiallyEmptyArray(final SArray arr, final SBlock block) {
    PartiallyEmptyArray storage = arr.getPartiallyEmptyStorage();
    int length = storage.getLength();
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(block, storage.get(SArray.FIRST_IDX));
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(block, storage.get(i));
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Specialization(guards = "arr.isObjectType()")
  public final SArray doObjectArray(final SArray arr, final SBlock block) {
    Object[] storage = arr.getObjectStorage();
    int length = storage.length;
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(block, storage[SArray.FIRST_IDX]);
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(block, storage[(int) i]);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Specialization(guards = "arr.isLongType()")
  public final SArray doLongArray(final SArray arr, final SBlock block) {
    long[] storage = arr.getLongStorage();
    int length = storage.length;
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(block, storage[SArray.FIRST_IDX]);
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(block, storage[(int) i]);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Specialization(guards = "arr.isDoubleType()")
  public final SArray doDoubleArray(final SArray arr, final SBlock block) {
    double[] storage = arr.getDoubleStorage();
    int length = storage.length;
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(block, storage[SArray.FIRST_IDX]);
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(block, storage[(int) i]);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Specialization(guards = "arr.isBooleanType()")
  public final SArray doBooleanArray(final SArray arr, final SBlock block) {
    boolean[] storage = arr.getBooleanStorage();
    int length = storage.length;
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(block, storage[SArray.FIRST_IDX]);
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(block, storage[(int) i]);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
