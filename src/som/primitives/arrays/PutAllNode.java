package som.primitives.arrays;

import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.specialized.SomLoop;
import som.primitives.SizeAndLengthPrim;
import som.vm.constants.Nil;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SBlock;
import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


@GenerateNodeFactory
@ImportStatic(Nil.class)
@NodeChild(value = "length", type = SizeAndLengthPrim.class, executeWith = "receiver")
public abstract class PutAllNode extends BinaryExpressionNode {
  @Child protected BlockDispatchNode block;

  public PutAllNode() {
    super(null);
    block = BlockDispatchNodeGen.create();
  }

  protected static final boolean valueOfNoOtherSpecialization(final Object value) {
    return !(value instanceof Long)    &&
           !(value instanceof Double)  &&
           !(value instanceof Boolean) &&
           !(value instanceof SBlock);
  }

  @Specialization(guards = {"rcvr.isEmptyType()", "valueIsNil(nil)"})
  public SMutableArray doPutNilInEmptyArray(final SMutableArray rcvr, final Object nil,
      final long length) {
    // NO OP
    return rcvr;
  }

  @Specialization(guards = {"valueIsNil(nil)"}, contains = {"doPutNilInEmptyArray"})
  public SMutableArray doPutNilInOtherArray(final SMutableArray rcvr, final SObjectWithClass nil,
      final long length) {
    rcvr.transitionToEmpty(length);
    return rcvr;
  }



  @Specialization
  public SMutableArray doPutEvalBlock(final VirtualFrame frame, final SMutableArray rcvr,
      final SBlock block, final long length) {
    if (length <= 0) {
      return rcvr;
    }

    try {
      Object newStorage = ArraySetAllStrategy.evaluateFirstDetermineStorageAndEvaluateRest(
          frame, block, length, this.block);
      rcvr.transitionTo(newStorage);
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
    return rcvr;
  }

  @Specialization
  public SMutableArray doPutLong(final SMutableArray rcvr, final long value,
      final long length) {
    rcvr.transitionToLongWithAll(length, value);
    return rcvr;
  }

  @Specialization
  public SMutableArray doPutDouble(final SMutableArray rcvr, final double value,
      final long length) {
    rcvr.transitionToDoubleWithAll(length, value);
    return rcvr;
  }

  @Specialization
  public SMutableArray doPutBoolean(final SMutableArray rcvr, final boolean value,
      final long length) {
    rcvr.transitionToBooleanWithAll(length, value);
    return rcvr;
  }

  @Specialization(guards = {"valueOfNoOtherSpecialization(value)"})
  public SMutableArray doPutObject(final SMutableArray rcvr, final Object value,
      final long length) {
    rcvr.transitionToObjectWithAll(length, value);
    return rcvr;
  }
}
