package tools.dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

import som.interpreter.ReturnException;
import som.interpreter.SomException;
import som.vm.NotYetImplementedException;
import tools.dym.profiles.TypeCounter;



/**
 * Simple execution event node which may be used in conjunction with `TypeCounter`
 * to count node activations while also keeping track of the respective result types.
 */
public class TypeCountingNode<T extends TypeCounter> extends ExecutionEventNode {
  protected final T counter;

  public TypeCountingNode(final T counter) {
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
      // If the SOMns code throws an exception, we should just ignore this.
      return;
    } else {
      throw new NotYetImplementedException();
    }
  }
}
