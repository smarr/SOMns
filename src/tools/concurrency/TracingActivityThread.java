package tools.concurrency;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

import som.vm.Activity;
import som.vm.ActivityThread;
import som.vm.VmSettings;
import tools.TraceData;
import tools.debugger.SteppingStrategy;
import tools.debugger.entities.EntityType;


public abstract class TracingActivityThread extends ForkJoinWorkerThread
    implements ActivityThread {
  public static AtomicInteger threadIdGen = new AtomicInteger(1);
  protected final long threadId;
  protected long nextActivityId = 1;
  protected long nextMessageId;
  protected long nextPromiseId;

  // Used for tracing, accessed by the ExecAllMessages classes
  public long createdMessages;
  public long resolvedPromises;
  public long erroredPromises;

  protected final TraceBuffer traceBuffer;

  protected SteppingStrategy steppingStrategy;

  protected ConcurrentEntityScope topEntity;

  private static class ConcurrentEntityScope {
    private final EntityType type;
    private final ConcurrentEntityScope next;

    ConcurrentEntityScope(final EntityType type, final ConcurrentEntityScope next) {
      this.type = type;
      this.next = next;
    }
  }

  public TracingActivityThread(final ForkJoinPool pool) {
    super(pool);
    if (VmSettings.ACTOR_TRACING) {
      traceBuffer = TraceBuffer.create();
      threadId = threadIdGen.getAndIncrement();
      nextActivityId = 1 + (threadId << TraceData.ACTIVITY_ID_BITS);
      nextMessageId = (threadId << TraceData.ACTIVITY_ID_BITS);
      nextPromiseId = (threadId << TraceData.ACTIVITY_ID_BITS);
    } else {
      threadId = 0;
      traceBuffer = null;
    }
    setName(getClass().getSimpleName() + "-" + threadId);
  }

  @Override
  public abstract Activity getActivity();

  @Override
  public SteppingStrategy getSteppingStrategy() {
    return this.steppingStrategy;
  }

  @Override
  public void setSteppingStrategy(final SteppingStrategy strategy) {
    this.steppingStrategy = strategy;
  }

  public void enterConcurrentScope(final EntityType type) {
    topEntity = new ConcurrentEntityScope(type, topEntity);
  }

  public void leaveConcurrentScope(final EntityType type) {
    assert topEntity.type == type;
    topEntity = topEntity.next;
  }

  public EntityType[] getConcurrentEntityScopes() {
    if (topEntity == null) {
      return null;
    }

    ArrayList<EntityType> list = new ArrayList<>();

    ConcurrentEntityScope current = topEntity;

    while (current != null) {
      list.add(current.type);
      current = current.next;
    }
    return list.toArray(new EntityType[0]);
  }

  public long generateActivityId() {
    long result = nextActivityId;
    nextActivityId++;
    assert TraceData.isWithinJSIntValueRange(result);
    return result;
  }

  public long generatePromiseId() {
    return nextPromiseId++;
  }

  public final TraceBuffer getBuffer() {
    return traceBuffer;
  }

  public abstract long getCurrentMessageId();

  @Override
  protected void onStart() {
    super.onStart();
    if (VmSettings.ACTOR_TRACING) {
      traceBuffer.init(ActorExecutionTrace.getEmptyBuffer(), threadId);
      ActorExecutionTrace.registerThread(this);
    }
  }

  @Override
  protected void onTermination(final Throwable exception) {
    if (VmSettings.ACTOR_TRACING) {
      traceBuffer.returnBuffer();
      ActorExecutionTrace.unregisterThread(this);
    }
    super.onTermination(exception);
  }

  public static TracingActivityThread currentThread() {
    return (TracingActivityThread) Thread.currentThread();
  }
}
