package tools.concurrency.nodes;

import com.oracle.truffle.api.nodes.Node;

import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.TracingActivityThread;


public abstract class TraceNode extends Node {

  protected static ActorTraceBuffer getCurrentBuffer() {
    TracingActivityThread t = TracingActivityThread.currentThread();
    return (ActorTraceBuffer) t.getBuffer();
  }
}
