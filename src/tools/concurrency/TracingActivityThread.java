package tools.concurrency;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

import som.vm.Activity;
import som.vm.VmSettings;
import tools.TraceData;
import tools.debugger.SteppingStrategy;
import tools.debugger.entities.EntityType;
import tools.debugger.entities.SteppingType;


public abstract class TracingActivityThread extends ForkJoinWorkerThread {
  public static AtomicInteger threadIdGen = new AtomicInteger(1);
  protected final long        threadId;
  protected long              nextEntityId;

  public static final int EXTERNAL_BUFFER_SIZE = 500;

  // Used for tracing, accessed by the ExecAllMessages classes
  public long createdMessages;
  public long resolvedPromises;
  public long erroredPromises;

  protected final TraceBuffer traceBuffer;
  protected Object[]          externalData;
  protected int               extIndex = 0;

  protected SteppingStrategy steppingStrategy;

  protected ConcurrentEntityScope topEntity;

  public volatile boolean swapTracingBuffer = false;

  private static class ConcurrentEntityScope {
    private final EntityType            type;
    private final ConcurrentEntityScope next;

    ConcurrentEntityScope(final EntityType type, final ConcurrentEntityScope next) {
      this.type = type;
      this.next = next;
    }
  }

  public TracingActivityThread(final ForkJoinPool pool) {
    super(pool);
    if (VmSettings.ACTOR_TRACING || VmSettings.MEDEOR_TRACING) {
      threadId = threadIdGen.getAndIncrement();
      traceBuffer = TraceBuffer.create(threadId);
      nextEntityId = 1 + (threadId << TraceData.ENTITY_ID_BITS);
      externalData = new Object[EXTERNAL_BUFFER_SIZE];
    } else {
      threadId = 0;
      nextEntityId = 0;
      traceBuffer = null;
      externalData = null;
    }
    setName(getClass().getSimpleName() + "-" + threadId);
  }

  public abstract Activity getActivity();

  public final boolean isStepping(final SteppingType type) {
    if (steppingStrategy == null) {
      return false;
    }
    return steppingStrategy.is(type);
  }

  public final void setSteppingStrategy(final SteppingType type) {
    this.steppingStrategy = new SteppingStrategy(type);
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

  /**
   * Generates a unique id, for all types of entities.
   */
  private long generateEntityId() {
    long result = nextEntityId;
    nextEntityId++;
    assert TraceData.isWithinJSIntValueRange(result);
    assert result != -1 : "-1 is not a valid entity id";
    return result;
  }

  public final TraceBuffer getBuffer() {
    return traceBuffer;
  }

  public final void addExternalData(final Object data) {
    externalData[extIndex] = data;
    extIndex++;
    if (extIndex == EXTERNAL_BUFFER_SIZE) {
      TracingBackend.addExternalData(externalData);
      externalData = new Object[EXTERNAL_BUFFER_SIZE];
      extIndex = 0;
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (VmSettings.ACTOR_TRACING) {
      TracingBackend.registerThread(this);
    }
  }

  @Override
  protected void onTermination(final Throwable exception) {
    if (VmSettings.ACTOR_TRACING) {
      traceBuffer.returnBuffer(null);
      TracingBackend.addExternalData(externalData);
      TracingBackend.unregisterThread(this);
    }
    super.onTermination(exception);
  }

  public static TracingActivityThread currentThread() {
    return (TracingActivityThread) Thread.currentThread();
  }

  public static long newEntityId() {
    if (VmSettings.MEDEOR_TRACING && Thread.currentThread() instanceof TracingActivityThread) {
      TracingActivityThread t = TracingActivityThread.currentThread();
      return t.generateEntityId();
    } else {
      return 0; // main actor
    }
  }

}
