package tools.superinstructions;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import som.interpreter.ReturnException;
import som.interpreter.SomException;
import som.vm.NotYetImplementedException;


/**
 * Execution event node to be used with {@link TypeCounter} to count node activations while
 * also keeping track of the result types.
 */
final class TypeCountingNode extends ExecutionEventNode {
  protected final TypeCounter counter;

  TypeCountingNode(final TypeCounter counter) {
    this.counter = counter;
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    counter.recordType(result);
  }

  @Override
  protected void onReturnExceptional(final VirtualFrame frame, final Throwable e) {
    // TODO: make language independent
    if (e instanceof ReturnException) {
      counter.recordType(((ReturnException) e).result());
    } else if (e instanceof UnexpectedResultException) {
      counter.recordType(((UnexpectedResultException) e).getResult());
    } else if (e instanceof SomException) {
      // TODO: why don't we consider exceptions as return types?
      // If the SOMns code throws an exception, we should just ignore this.
      return;
    } else {
      throw new NotYetImplementedException();
    }
  }
}
