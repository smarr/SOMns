package som.interpreter.nodes.specialized;

import som.interpreter.Invokable;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;


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
        reportLoopCount(limit - receiver);
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
        reportLoopCount((int) limit - receiver);
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

  private void reportLoopCount(final long count) {
    CompilerAsserts.neverPartOfCompilation("reportLoopCount");
    Node current = getParent();
    while (current != null && !(current instanceof RootNode)) {
      current = current.getParent();
    }
    if (current != null) {
      ((Invokable) current).propagateLoopCountThroughoutMethodScope(count);
    }
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
