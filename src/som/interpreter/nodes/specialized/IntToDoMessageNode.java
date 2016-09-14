package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.VmSettings;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.specialized.IntToDoMessageNode.ToDoSplzr;
import som.primitives.Primitive;
import som.vm.Primitives.Specializer;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.LoopNode;


@GenerateNodeFactory
@Primitive(selector = "to:do:", noWrapper = true, disabled = true,
           specializer = ToDoSplzr.class)
public abstract class IntToDoMessageNode extends TernaryExpressionNode {
  public static class ToDoSplzr extends Specializer<IntToDoMessageNode> {
    public ToDoSplzr(final Primitive prim, final NodeFactory<IntToDoMessageNode> fact) { super(prim, fact); }

    @Override
    public boolean matches(final Object[] args,
        final ExpressionNode[] argNodes) {
      return !VmSettings.DYNAMIC_METRICS && args[0] instanceof Long &&
          (args[1] instanceof Long || args[1] instanceof Double) &&
          args[2] instanceof SBlock;
    }
  }

  protected static final DirectCallNode create(final SInvokable blockMethod) {
    return Truffle.getRuntime().createDirectCallNode(blockMethod.getCallTarget());
  }

  protected IntToDoMessageNode(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
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
