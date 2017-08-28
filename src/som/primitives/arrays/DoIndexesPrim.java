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
import som.primitives.SizeAndLengthPrim;
import som.primitives.SizeAndLengthPrimFactory;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;


@GenerateNodeFactory
@Primitive(selector = "doIndexes:", receiverType = SArray.class, disabled = true)
public abstract class DoIndexesPrim extends BinaryComplexOperation {
  @Child protected BlockDispatchNode block  = BlockDispatchNodeGen.create();
  @Child protected SizeAndLengthPrim length = SizeAndLengthPrimFactory.create(null);
  // TODO: tag properly, this is a loop, but without array access

  @Specialization
  public final SArray doArray(final SArray receiver, final SBlock block) {
    int length = (int) this.length.executeEvaluated(receiver);
    loop(block, length);
    return receiver;
  }

  private void loop(final SBlock block, final int length) {
    try {
      int expectedFirstIdx = 0; // this code is written with this expectation
      assert SArray.FIRST_IDX == expectedFirstIdx;

      if (SArray.FIRST_IDX < length) {
        this.block.executeDispatch(new Object[] {
            // +1 because it is going to the smalltalk level
            block, (long) SArray.FIRST_IDX + 1});
      }
      for (long i = 1; i < length; i++) {
        this.block.executeDispatch(new Object[] {
            block, i + 1}); // +1 because it is going to the smalltalk level
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(length, this);
      }
    }
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
