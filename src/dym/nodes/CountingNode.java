package dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

import dym.profiles.Counter;


public class CountingNode<T extends Counter> extends ExecutionEventNode {

  protected final T counter;

  public CountingNode(final T counter) {
    this.counter = counter;
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    counter.inc();
  }

  public T getProfile() {
    return counter;
  }
}
