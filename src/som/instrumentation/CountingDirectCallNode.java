package som.instrumentation;

import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;

import som.interpreter.Invokable;


public final class CountingDirectCallNode extends DirectCallNode {

  @Child private DirectCallNode callNode;

  private final AtomicInteger counter;

  public CountingDirectCallNode(final DirectCallNode callNode) {
    super(null);
    this.callNode = callNode;
    this.counter = new AtomicInteger();
  }

  @Override
  public Object call(final Object... arguments) {
    counter.incrementAndGet();
    return callNode.call(arguments);
  }

  public int getCount() {
    return counter.get();
  }

  public Invokable getInvokable() {
    return (Invokable) callNode.getCurrentRootNode();
  }

  @Override
  public boolean isInlinable() {
    throw new UnsupportedOperationException(
        "Only to be used in Dynamic Metric Tool, where Truffle is not expected to inline");
    // return callNode.isInlinable();
  }

  @Override
  public boolean isInliningForced() {
    throw new UnsupportedOperationException(
        "Only to be used in Dynamic Metric Tool, where Truffle is not expected to inline");
    // return callNode.isInliningForced();
  }

  @Override
  public void forceInlining() {
    throw new UnsupportedOperationException(
        "Only to be used in Dynamic Metric Tool, where Truffle is not expected to inline");
    // callNode.forceInlining();
  }

  @Override
  public boolean isCallTargetCloningAllowed() {
    throw new UnsupportedOperationException(
        "Only to be used in Dynamic Metric Tool, where Truffle is not expected to inline");
    // return callNode.isCallTargetCloningAllowed();
  }

  @Override
  public boolean cloneCallTarget() {
    throw new UnsupportedOperationException(
        "Only to be used in Dynamic Metric Tool, where Truffle is not expected to inline");
    // return callNode.cloneCallTarget();
  }

  @Override
  public CallTarget getClonedCallTarget() {
    throw new UnsupportedOperationException(
        "Only to be used in Dynamic Metric Tool, where Truffle is not expected to inline");
    // return callNode.getClonedCallTarget();
  }

}
