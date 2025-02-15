package tools.replay.nodes;

import com.oracle.truffle.api.nodes.Node;

import tools.concurrency.TracingActivityThread;
import tools.replay.actors.UniformExecutionTrace.UniformTraceBuffer;


public abstract class TraceNode extends Node {

  protected static UniformTraceBuffer getCurrentBuffer() {
    TracingActivityThread t = TracingActivityThread.currentThread();
    return (UniformTraceBuffer) t.getBuffer();
  }
}
