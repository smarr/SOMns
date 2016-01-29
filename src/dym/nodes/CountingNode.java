package dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventNode;

import dym.DynamicMetrics;
import dym.profiles.Counter;


public class CountingNode extends EventNode {

  protected final Counter counter;

  public CountingNode(final Counter counter) {
    this.counter = counter;
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    counter.inc();
  }
}
