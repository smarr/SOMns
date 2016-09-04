package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.specialized.IntToByDoMessageNode.Splzr;
import som.primitives.Primitive;
import som.vm.Primitives.Specializer;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.LoopNode;


@GenerateNodeFactory
@Primitive(selector = "to:by:do:", disabled = true, specializer = Splzr.class, noWrapper = true)
public abstract class IntToByDoMessageNode extends QuaternaryExpressionNode {
  public static class Splzr extends Specializer {
    @Override
    public <T> T create(final NodeFactory<T> factory, final Object[] arguments,
        final ExpressionNode[] argNodes, final SourceSection section,
        final boolean eagerWrapper) {
      return factory.createNode(section, arguments[3], argNodes[0],
          argNodes[1], argNodes[2], argNodes[3]);
    }
  }

  private final SInvokable blockMethod;
  @Child private DirectCallNode valueSend;

  public IntToByDoMessageNode(final SourceSection section, final SBlock block) {
    super(false, section);
    blockMethod = block.getMethod();
    valueSend = Truffle.getRuntime().createDirectCallNode(
                    blockMethod.getCallTarget());
  }

  public IntToByDoMessageNode(final IntToByDoMessageNode node) {
    super(false, node.getSourceSection());
    this.blockMethod = node.blockMethod;
    this.valueSend   = node.valueSend;
  }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  protected final boolean isSameBlockLong(final SBlock block) {
    return block.getMethod() == blockMethod;
  }

  protected final boolean isSameBlockDouble(final SBlock block) {
    return block.getMethod() == blockMethod;
  }

  @Specialization(guards = "isSameBlockLong(block)")
  public final long doIntToByDo(final VirtualFrame frame, final long receiver, final long limit, final long step, final SBlock block) {
    try {
      if (receiver <= limit) {
        valueSend.call(frame, new Object[] {block, receiver});
      }
      for (long i = receiver + step; i <= limit; i += step) {
        valueSend.call(frame, new Object[] {block, i});
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount(limit - receiver, this);
      }
    }
    return receiver;
  }

  @Specialization(guards = "isSameBlockDouble(block)")
  public final long doIntToByDo(final VirtualFrame frame, final long receiver, final double limit, final long step, final SBlock block) {
    try {
      if (receiver <= limit) {
        valueSend.call(frame, new Object[] {block, receiver});
      }
      for (long i = receiver + step; i <= limit; i += step) {
        valueSend.call(frame, new Object[] {block, i});
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        SomLoop.reportLoopCount((long) limit - receiver, this);
      }
    }
    return receiver;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
