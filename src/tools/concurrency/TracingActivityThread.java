package tools.concurrency;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

import som.VM;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.vm.Activity;
import som.vm.VmSettings;
import tools.TraceData;
import tools.debugger.SteppingStrategy;
import tools.debugger.entities.EntityType;
import tools.debugger.entities.SteppingType;
import tools.replay.TraceParser;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;


public abstract class TracingActivityThread extends ForkJoinWorkerThread {
  private final VM vm;

  public static AtomicInteger threadIdGen = new AtomicInteger(1);
  protected final long        threadId;
  protected long              nextEntityId;
  protected byte              snapshotId;

  public static final int EXTERNAL_BUFFER_SIZE = 500;

  // Used for tracing, accessed by the ExecAllMessages classes
  public long createdMessages;
  public long resolvedPromises;
  public long erroredPromises;

  protected final TraceBuffer traceBuffer;
  protected SnapshotBuffer    snapshotBuffer;
  protected Object[]          externalData;
  protected int               extIndex = 0;

  protected SteppingStrategy steppingStrategy;

  protected ConcurrentEntityScope topEntity;

  protected volatile boolean swapTracingBuffer = false;
  protected boolean          suspendedInDebugger;

  private static class ConcurrentEntityScope {
    private final EntityType            type;
    private final ConcurrentEntityScope next;

    ConcurrentEntityScope(final EntityType type, final ConcurrentEntityScope next) {
      this.type = type;
      this.next = next;
    }
  }

  /**
   * Swap the tracing buffer, if it was requested.
   *
   * <p>
   * This method is unsynchronized, and should only be called by the thread itself.
   * Or when we know that the thread is blocked, for instance in a breakpoint.
   */
  public void swapTracingBufferIfRequestedUnsync() {
    if (swapTracingBuffer) {
      getBuffer().swapStorage();
      swapTracingBuffer = false;
    }
  }

  public synchronized void markThreadAsSuspendedInDebugger() {
    suspendedInDebugger = true;
  }

  public synchronized void markThreadAsResumedFromDebugger() {
    suspendedInDebugger = false;
  }

  public synchronized boolean swapTracingBufferIfThreadSuspendedInDebugger() {
    if (suspendedInDebugger) {
      swapTracingBufferIfRequestedUnsync();
      return true;
    }
    return false;
  }

  public TracingActivityThread(final ForkJoinPool pool, final VM vm) {
    super(pool);
    this.vm = vm;

    if (VmSettings.SNAPSHOTS_ENABLED) {
      this.snapshotBuffer = new SnapshotBuffer((ActorProcessingThread) this);
    }

    if (VmSettings.ACTOR_TRACING || VmSettings.KOMPOS_TRACING) {
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
      TracingBackend.addExternalData(externalData, this);
      externalData = new Object[EXTERNAL_BUFFER_SIZE];
      extIndex = 0;
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (VmSettings.ACTOR_TRACING || VmSettings.KOMPOS_TRACING) {
      TracingBackend.registerThread(this);
    }
    vm.enterContext();
  }

  @Override
  protected void onTermination(final Throwable exception) {
    if (VmSettings.ACTOR_TRACING || VmSettings.KOMPOS_TRACING) {
      traceBuffer.returnBuffer(null);
      TracingBackend.addExternalData(externalData, this);
      TracingBackend.unregisterThread(this);
    }
    if (VmSettings.SNAPSHOTS_ENABLED) {
      SnapshotBackend.registerSnapshotBuffer(snapshotBuffer);
    }

    vm.leaveContext();
    super.onTermination(exception);
  }

  public static TracingActivityThread currentThread() {
    return (TracingActivityThread) Thread.currentThread();
  }

  public static long newEntityId() {
    if (VmSettings.REPLAY && Thread.currentThread() instanceof TracingActivityThread) {
      return TraceParser.getReplayId();
    } else if ((VmSettings.KOMPOS_TRACING | VmSettings.ACTOR_TRACING)
        && Thread.currentThread() instanceof TracingActivityThread) {
      TracingActivityThread t = TracingActivityThread.currentThread();
      return t.generateEntityId();
    } else {
      return 0; // main actor
    }
  }

  public long getThreadId() {
    return threadId;
  }

  public SnapshotBuffer getSnapshotBuffer() {
    if (SnapshotBackend.getSnapshotVersion() > this.snapshotId) {
      newSnapshot();
    }
    return snapshotBuffer;
  }

  public byte getSnapshotId() {
    return snapshotId;
  }

  private void newSnapshot() {
    traceBuffer.swapStorage();
    if (extIndex != 0) {
      TracingBackend.addExternalData(externalData, this);
      externalData = new Object[EXTERNAL_BUFFER_SIZE];
      extIndex = 0;
    }
    this.snapshotId = SnapshotBackend.getSnapshotVersion();

    // get net snapshotbuffer
    this.snapshotBuffer = new SnapshotBuffer((ActorProcessingThread) this);
  }

}
