package som.primitives.arrays;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.specialized.SomLoop;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.PartiallyEmptyArray;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;


@GenerateNodeFactory
public abstract class DoPrim extends BinaryComplexOperation {
  private final ValueProfile storageType = ValueProfile.createClassProfile();

  @Child private BlockDispatchNode block;

  public DoPrim(final SourceSection source) {
    super(source);
    block = BlockDispatchNodeGen.create();
  }

  private void execBlock(final VirtualFrame frame, final SBlock block, final Object arg) {
    this.block.executeDispatch(frame, new Object[] {block, arg});
  }

  @Specialization(guards = "arr.isEmptyType()")
  public final SArray doEmptyArray(final VirtualFrame frame,
      final SArray arr, final SBlock block) {
    int length = arr.getEmptyStorage(storageType);
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(frame, block, Nil.nilObject);
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(frame, block, Nil.nilObject);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Specialization(guards = "arr.isPartiallyEmptyType()")
  public final SArray doPartiallyEmptyArray(final VirtualFrame frame,
      final SArray arr, final SBlock block) {
    PartiallyEmptyArray storage = arr.getPartiallyEmptyStorage(storageType);
    int length = storage.getLength();
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(frame, block, storage.get(SArray.FIRST_IDX));
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(frame, block, storage.get(i));
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Specialization(guards = "arr.isObjectType()")
  public final SArray doObjectArray(final VirtualFrame frame,
      final SArray arr, final SBlock block) {
    Object[] storage = arr.getObjectStorage(storageType);
    int length = storage.length;
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(frame, block, storage[SArray.FIRST_IDX]);
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(frame, block, storage[(int) i]);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Specialization(guards = "arr.isLongType()")
  public final SArray doLongArray(final VirtualFrame frame,
      final SArray arr, final SBlock block) {
    long[] storage = arr.getLongStorage(storageType);
    int length = storage.length;
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(frame, block, storage[SArray.FIRST_IDX]);
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(frame, block, storage[(int) i]);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Specialization(guards = "arr.isDoubleType()")
  public final SArray doDoubleArray(final VirtualFrame frame,
      final SArray arr, final SBlock block) {
    double[] storage = arr.getDoubleStorage(storageType);
    int length = storage.length;
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(frame, block, storage[SArray.FIRST_IDX]);
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(frame, block, storage[(int) i]);
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return arr;
  }

  @Specialization(guards = "arr.isBooleanType()")
  public final SArray doBooleanArray(final VirtualFrame frame,
      final SArray arr, final SBlock block) {
    boolean[] storage = arr.getBooleanStorage(storageType);
    int length = storage.length;
    try {
      if (SArray.FIRST_IDX < length) {
        execBlock(frame, block, storage[SArray.FIRST_IDX]);
      }
      for (long i = SArray.FIRST_IDX + 1; i < length; i++) {
        execBlock(frame, block, storage[(int) i]);
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
