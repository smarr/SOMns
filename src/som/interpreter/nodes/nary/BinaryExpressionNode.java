package som.interpreter.nodes.nary;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;


@NodeChildren({
  @NodeChild(value = "receiver", type = ExpressionNode.class),
  @NodeChild(value = "argument", type = ExpressionNode.class)})
public abstract class BinaryExpressionNode extends ExpressionNode
    implements PreevaluatedExpression {

  public BinaryExpressionNode(final SourceSection source) {
    super(source);
  }

  // for nodes that are not representing source code
  public BinaryExpressionNode() { super(null); }

  public abstract Object executeEvaluated(final VirtualFrame frame,
      final Object receiver, Object argument);

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame,
      final Object[] arguments) {
    return executeEvaluated(frame,
        CompilerDirectives.unsafeCast(arguments[0], Object.class, true, true),
        CompilerDirectives.unsafeCast(arguments[1], Object.class, true, true));
  }
}
