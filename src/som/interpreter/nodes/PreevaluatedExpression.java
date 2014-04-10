package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;


public interface PreevaluatedExpression {
  Object executePreEvaluated(final VirtualFrame frame, final Object[] args);
}
