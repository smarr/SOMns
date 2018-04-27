package tools.concurrency.nodes;

import com.oracle.truffle.api.nodes.Node;

import som.vm.VmSettings;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.TracingActor;


public abstract class TraceNode extends Node {

  public abstract void execute(TracingActor actor);

  protected static boolean smallIds() {
    return VmSettings.TRACE_SMALL_IDS;
  }

  protected static boolean byteId(final TracingActor actor) {
    return (actor.getActorId() & 0xFFFFFF00) == 0;
  }

  protected static boolean shortId(final TracingActor actor) {
    return (actor.getActorId() & 0xFFFF0000) == 0;
  }

  protected static boolean threeByteId(final TracingActor actor) {
    return (actor.getActorId() & 0xFF000000) == 0;
  }

  protected static ActorTraceBuffer getCurrentBuffer() {
    TracingActivityThread t = TracingActivityThread.currentThread();
    return (ActorTraceBuffer) t.getBuffer();
  }
}
