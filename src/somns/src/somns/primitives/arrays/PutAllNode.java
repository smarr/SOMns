package somns.primitives.arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import somns.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import somns.primitives.SizeAndLengthPrimFactory;
import somns.interpreter.nodes.dispatch.BlockDispatchNode;
import somns.interpreter.nodes.nary.BinaryComplexOperation;
import somns.interpreter.nodes.nary.UnaryExpressionNode;
import somns.interpreter.nodes.specialized.SomLoop;
import somns.vm.constants.Nil;
import somns.vmobjects.SBlock;
import somns.vmobjects.SObjectWithClass;
import somns.vmobjects.SArray.SMutableArray;


@GenerateNodeFactory
@ImportStatic(Nil.class)
@Primitive(selector = "putAll:", disabled = true,
    extraChild = SizeAndLengthPrimFactory.class, receiverType = SMutableArray.class)
@NodeChild(value = "length", type = UnaryExpressionNode.class, executeWith = "receiver")
public abstract class PutAllNode extends BinaryComplexOperation {
  @Child protected BlockDispatchNode block = BlockDispatchNodeGen.create();
  // TODO: tag properly, it is a loop, and array access

  protected static final boolean valueOfNoOtherSpecialization(final Object value) {
    return !(value instanceof Long) &&
        !(value instanceof Double) &&
        !(value instanceof Boolean) &&
        !(value instanceof SBlock);
  }

  @Specialization(guards = {"rcvr.isEmptyType()", "valueIsNil(nil)"})
  public SMutableArray doPutNilInEmptyArray(final SMutableArray rcvr, final Object nil,
      final long length) {
    // NO OP
    return rcvr;
  }

  @Specialization(guards = {"valueIsNil(nil)"}, replaces = {"doPutNilInEmptyArray"})
  public SMutableArray doPutNilInOtherArray(final SMutableArray rcvr,
      final SObjectWithClass nil,
      final long length) {
    rcvr.transitionToEmpty(length);
    return rcvr;
  }

  @Specialization
  public SMutableArray doPutEvalBlock(final SMutableArray rcvr,
      final SBlock block, final long length) {
    if (length <= 0) {
      return rcvr;
    }

    try {
      Object newStorage = ArraySetAllStrategy.evaluateFirstDetermineStorageAndEvaluateRest(
          block, length, this.block);
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
