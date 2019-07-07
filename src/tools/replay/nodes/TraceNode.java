package tools.replay.nodes;

import com.oracle.truffle.api.nodes.Node;

import tools.concurrency.TracingActivityThread;
import tools.replay.actors.ActorExecutionTrace.ActorTraceBuffer;


public abstract class TraceNode extends Node {

  protected static ActorTraceBuffer getCurrentBuffer() {
    TracingActivityThread t = TracingActivityThread.currentThread();
    return (ActorTraceBuffer) t.getBuffer();
  }
}
