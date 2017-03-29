package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;


public interface PreevaluatedExpression {
  Object doPreEvaluated(VirtualFrame frame, Object[] args);
}
