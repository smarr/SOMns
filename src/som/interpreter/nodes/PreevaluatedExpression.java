package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;


public interface PreevaluatedExpression {
  Object executeEvaluated(final VirtualFrame frame, final Object receiver,
      final Object[] arguments);
}
