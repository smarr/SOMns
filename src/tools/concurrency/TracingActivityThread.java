package tools.concurrency;

import java.nio.ByteBuffer;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

import som.vm.VmSettings;
import tools.TraceData;


public abstract class TracingActivityThread extends ForkJoinWorkerThread {
  private static AtomicInteger threadIdGen = new AtomicInteger(0);
  protected final long threadId;

  protected long nextActivityId = 1;

  protected long nextMessageId;
  protected long nextPromiseId;

  // Used for tracing, accessed by the ExecAllMessages classes
  public long createdMessages;
  public long resolvedPromises;

  protected ByteBuffer tracingDataBuffer;

  public TracingActivityThread(final ForkJoinPool pool) {
    super(pool);
    if (VmSettings.ACTOR_TRACING) {
      ActorExecutionTrace.swapBuffer(this);
      threadId = threadIdGen.getAndIncrement();
      nextMessageId = (threadId << TraceData.ACTIVITY_ID_BITS);
      nextPromiseId = (threadId << TraceData.ACTIVITY_ID_BITS);
    } else {
      threadId = 0;
    }
  }

  public long generateActivityId() {
    long result = (threadId << TraceData.ACTIVITY_ID_BITS) | nextActivityId;
    nextActivityId++;
    assert TraceData.isWithinJSIntValueRange(result);
    return result;
  }

  public long generatePromiseId() {
    return nextPromiseId++;
  }

  public final ByteBuffer getThreadLocalBuffer() {
    return tracingDataBuffer;
  }

  public void setThreadLocalBuffer(final ByteBuffer threadLocalBuffer) {
    this.tracingDataBuffer = threadLocalBuffer;
  }

  public abstract long getCurrentMessageId();

  @Override
  protected void onTermination(final Throwable exception) {
    if (VmSettings.ACTOR_TRACING) {
      ActorExecutionTrace.returnBuffer(this.tracingDataBuffer);
      this.tracingDataBuffer = null;
    }
    super.onTermination(exception);
  }
}
