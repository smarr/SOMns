package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.Primitive;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.LoopNode;


@GenerateNodeFactory
@Primitive(selector = "to:by:do:", disabled = true, noWrapper = true, requiresArguments = true)
public abstract class IntToByDoMessageNode extends QuaternaryExpressionNode {
  protected final SInvokable      blockMethod;
  @Child protected DirectCallNode valueSend;

  public IntToByDoMessageNode(final boolean eagWrap,
      final SourceSection section, final Object[] args) {
    super(eagWrap, section);
    assert !eagWrap;
    blockMethod = ((SBlock) args[3]).getMethod();
    valueSend = Truffle.getRuntime().createDirectCallNode(
        blockMethod.getCallTarget());
  }

  public IntToByDoMessageNode(final IntToByDoMessageNode node) {
    super(false, node.getSourceSection());
    this.blockMethod = node.blockMethod;
    this.valueSend = node.valueSend;
  }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @Specialization(guards = "block.getMethod() == blockMethod")
  public final long doIntToByDo(final long receiver,
      final long limit, final long step, final SBlock block) {
    return doLoop(valueSend, this, receiver, limit, step, block);
  }

  @Specialization(guards = "block.getMethod() == blockMethod")
  public final long doIntToByDo(final long receiver,
      final double limit, final long step, final SBlock block) {
    return doLoop(valueSend, this, receiver, (long) limit, step, block);
  }

  public static long doLoop(final DirectCallNode value,
      final Node loopNode, final long receiver, final long limit, final long step,
      final SBlock block) {
    try {
      if (receiver <= limit) {
        value.call(new Object[] {block, receiver});
      }
      for (long i = receiver + step; i <= limit; i += step) {
        value.call(new Object[] {block, i});
        ObjectTransitionSafepoint.INSTANCE.checkAndPerformSafepoint();
      }
    } finally {
      if (CompilerDirectives.inInterpreter()) {
        long loopCount = limit - receiver;
        if (loopCount > 0) {
          SomLoop.reportLoopCount(loopCount, loopNode);
        }
      }
    }
    return receiver;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
