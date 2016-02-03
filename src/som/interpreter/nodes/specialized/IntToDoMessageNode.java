package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;


public abstract class IntToDoMessageNode extends TernaryExpressionNode {

  protected static final DirectCallNode create(final SInvokable blockMethod) {
    return Truffle.getRuntime().createDirectCallNode(blockMethod.getCallTarget());
  }

  @Specialization(guards = "block.getMethod() == blockMethod")
  public final long doIntToDo(final VirtualFrame frame, final long receiver,
      final long limit, final SBlock block,
      @Cached("block.getMethod()") final SInvokable blockMethod,
      @Cached("create(blockMethod)") final DirectCallNode valueSend) {
    try {
      doLooping(frame, receiver, limit, block, valueSend);
    } finally {
      if (CompilerDirectives.inInterpreter() && (limit - receiver) > 0) {
        SomLoop.reportLoopCount(limit - receiver, this);
      }
    }
    return receiver;
  }

  @Specialization(guards = "block.getMethod() == blockMethod")
  public final long doIntToDo(final VirtualFrame frame, final long receiver,
      final double dLimit, final SBlock block,
      @Cached("block.getMethod()") final SInvokable blockMethod,
      @Cached("create(blockMethod)") final DirectCallNode valueSend) {
    long limit = (long) dLimit;
    try {
      doLooping(frame, receiver, limit, block, valueSend);
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount((int) limit - receiver, this);
      }
    }
    return receiver;
  }

  protected void doLooping(final VirtualFrame frame, final long receiver,
      final long limit, final SBlock block, final DirectCallNode valueSend) {
    if (receiver <= limit) {
      valueSend.call(frame, new Object[] {block, receiver});
    }
    for (long i = receiver + 1; i <= limit; i++) {
      valueSend.call(frame, new Object[] {block, i});
    }
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
