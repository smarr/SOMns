package tools.concurrency;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.sun.management.GarbageCollectionNotificationInfo;

import som.Output;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.vm.NotYetImplementedException;
import som.vm.VmSettings;
import som.vmobjects.SSymbol;
import tools.debugger.FrontendConnector;
import tools.debugger.entities.Implementation;


/**
 * Tracing of the execution is done on a per-thread basis to minimize overhead
 * at run-time.
 *
 * <p>
 * To communicate detailed information about the system for debugging,
 * TraceWorkerThreads will capture the available data periodically and send it
 * to the debugger.
 *
 * <h4>Synchronization Strategy</h4>
 *
 * <p>
 * During normal execution with tracing, we do not need any synchronization,
 * because all operations on the buffers are initiated by the
 * TracingActivityThreads themselves. Thus, everything is thread-local. The
 * swapping of buffers is initiated from the TracingActivityThreads, too.
 * It requires synchronization only on the data structure where the buffers
 * are listed.
 *
 * <p>
 * During execution with the debugger however, the periodic data requests
 * cause higher synchronization requirements. Since this is about interactive
 * debugging the cost of synchronization is likely acceptable, and will simply
 * done on the buffer for all access.
 */
public class TracingBackend {
  private static final int BUFFER_POOL_SIZE = VmSettings.NUM_THREADS * 4;
  static final int         BUFFER_SIZE      = 4096 * 128;

  private static final int TRACE_TIMEOUT = 500;
  private static final int POLL_TIMEOUT  = 100;

  private static final List<java.lang.management.GarbageCollectorMXBean> gcbeans =
      ManagementFactory.getGarbageCollectorMXBeans();

  private static final ArrayBlockingQueue<ByteBuffer> emptyBuffers =
      new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);
  private static final ArrayBlockingQueue<ByteBuffer> fullBuffers  =
      new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);

  private static final LinkedList<ByteBuffer> externalData = new LinkedList<>();

  // contains symbols that need to be written to file/sent to debugger,
  // e.g. actor type, message type
  private static final ArrayList<SSymbol> symbolsToWrite = new ArrayList<>();

  private static FrontendConnector front = null;

  private static long              collectedMemory = 0;
  private static TraceWorkerThread workerThread    = new TraceWorkerThread();

  static {
    if (VmSettings.MEMORY_TRACING) {
      setUpGCMonitoring();
    }

    if (VmSettings.ACTOR_TRACING) {
      for (int i = 0; i < BUFFER_POOL_SIZE; i++) {
        emptyBuffers.add(ByteBuffer.allocate(BUFFER_SIZE));
      }
    }
  }

  private static long getTotal(final Map<String, MemoryUsage> map) {
    return map.entrySet().stream().mapToLong(usage -> usage.getValue().getUsed()).sum();
  }

  public static void setUpGCMonitoring() {
    for (java.lang.management.GarbageCollectorMXBean bean : gcbeans) {
      NotificationEmitter emitter = (NotificationEmitter) bean;
      NotificationListener listener = new NotificationListener() {
        @Override
        public void handleNotification(final Notification notification,
            final Object handback) {
          if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(
              notification.getType())) {
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(
                (CompositeData) notification.getUserData());
            long after = getTotal(info.getGcInfo().getMemoryUsageAfterGc());
            long before = getTotal(info.getGcInfo().getMemoryUsageBeforeGc());
            collectedMemory += before - after;
          }
        }
      };
      emitter.addNotificationListener(listener, null, null);
    }
  }

  public static void reportPeakMemoryUsage() {
    List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
    long totalHeap = 0;
    long totalNonHeap = 0;
    long gcTime = 0;
    for (MemoryPoolMXBean memoryPoolMXBean : pools) {
      long peakUsed = memoryPoolMXBean.getPeakUsage().getUsed();
      if (memoryPoolMXBean.getType() == MemoryType.HEAP) {
        totalHeap += peakUsed;
      } else if (memoryPoolMXBean.getType() == MemoryType.NON_HEAP) {
        totalNonHeap += peakUsed;
      }
    }
    for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
      gcTime += garbageCollectorMXBean.getCollectionTime();
    }
    Output.println("[Memstat] Heap: " + totalHeap + "B\tNonHeap: " + totalNonHeap
        + "B\tCollected: " + collectedMemory + "B\tGC-Time: " + gcTime + "ms");
  }

  @TruffleBoundary
  public static ByteBuffer getEmptyBuffer() {
    try {
      return emptyBuffers.take();
    } catch (InterruptedException e) {
      throw new IllegalStateException("Failed to acquire a new Buffer!");
    }
  }

  static void returnBuffer(final ByteBuffer b) {
    if (b == null) {
      return;
    }

    returnBufferGlobally(b);
  }

  @TruffleBoundary
  private static void returnBufferGlobally(final ByteBuffer b) {
    fullBuffers.offer(b);
  }

  private static HashSet<TracingActivityThread> tracingThreads = new HashSet<>();

  public static void registerThread(final TracingActivityThread t) {
    synchronized (tracingThreads) {
      boolean added = tracingThreads.add(t);
      assert added;
    }
  }

  public static void unregisterThread(final TracingActivityThread t) {
    synchronized (tracingThreads) {
      boolean removed = tracingThreads.remove(t);
      assert removed;
      t.swapTracingBuffer = false;
    }
  }

  public static final void forceSwapBuffers() {
    assert VmSettings.ACTOR_TRACING
        || (VmSettings.TRUFFLE_DEBUGGER_ENABLED && VmSettings.MEDEOR_TRACING);
    TracingActivityThread[] result;
    synchronized (tracingThreads) {
      result = tracingThreads.toArray(new TracingActivityThread[0]);
    }

    // XXX: This is only safe because we assume that threads do not disappear
    // XXX: correction, I think this is all inherently racy, but hopefully good enough

    // signal threads to swap buffers
    for (TracingActivityThread t : result) {
      t.swapTracingBuffer = true;
    }

    TracingActivityThread current = TracingActivityThread.currentThread();
    int runningThreads = result.length;
    while (runningThreads > 1) {
      for (int i = 0; i < result.length; i += 1) {
        TracingActivityThread t = result[i];
        if (t == null) {
          continue;
        }

        if (!t.swapTracingBuffer) { // indicating that it was swapped
          runningThreads -= 1;
          result[i] = null;
        } else if (t instanceof ActorProcessingThread) {
          // if the thread is not currently having an actor, it is not executing
          if (((ActorProcessingThread) t).currentlyExecutingActor == null || t == current) {
            runningThreads -= 1;
            result[i] = null;
            t.getBuffer().swapStorage();
          }
        }
      }
    }

    while (!fullBuffers.isEmpty()) {
      // wait until the worker thread processed all the buffers
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static final long[] getStatistics() {
    long[] stats = new long[] {workerThread.traceBytes, workerThread.externalBytes};

    workerThread.traceBytes = 0;
    workerThread.externalBytes = 0;
    return stats;
  }

  @TruffleBoundary
  public static final void addExternalData(final ByteBuffer b) {
    if (b == null) {
      return;
    }

    synchronized (externalData) {
      externalData.add(b);
    }
  }

  public static void startTracingBackend() {
    // start worker thread for trace processing
    workerThread.start();
  }

  /**
   * @param fc The FrontendConnector used to send data to the debugger.
   */
  public static void setFrontEnd(final FrontendConnector fc) {
    front = fc;
  }

  public static void logSymbol(final SSymbol symbol) {
    synchronized (symbolsToWrite) {
      symbolsToWrite.add(symbol);
    }
  }

  public static void waitForTrace() {
    workerThread.cont = false;
    try {
      workerThread.join(TRACE_TIMEOUT);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static class TraceWorkerThread extends Thread {
    protected boolean cont = true;

    protected long traceBytes;
    protected long externalBytes;

    private ByteBuffer ignoreEmptyBuffer(final ByteBuffer buffer) {
      // Ignore buffers that only contain the thread index
      if (buffer.position() <= Implementation.IMPL_THREAD.getSize() +
          Implementation.IMPL_CURRENT_ACTIVITY.getSize()) {

        buffer.clear();
        TracingBackend.emptyBuffers.add(buffer);
        return null;
      } else {
        return buffer;
      }
    }

    private ByteBuffer tryToObtainBuffer() {
      ByteBuffer buffer;
      try {
        buffer = TracingBackend.fullBuffers.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        if (buffer == null) {
          if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
            // swap all non-empty buffers and try again
            // TracingBackend.forceSwapBuffers();

            // TODO: implement buffer swapping for debugger again,
            // but need an implementation that's decoupled from the trace stats primitive
            throw new NotYetImplementedException();
          }
          return null;
        } else {
          return ignoreEmptyBuffer(buffer);
        }
      } catch (InterruptedException e) {
        return null;
      }
    }

    private void writeExternalData(final FileOutputStream edfos) throws IOException {
      synchronized (externalData) {
        while (!externalData.isEmpty()) {
          ByteBuffer data = externalData.removeFirst();

          externalBytes += data.position();
          if (edfos != null) {
            edfos.getChannel()
                 .write(data.getReadingFromStartBuffer());
            edfos.flush();
          }
        }
      }
    }

    private void writeSymbols(final BufferedWriter bw) throws IOException {
      synchronized (symbolsToWrite) {
        if (front != null) {
          front.sendSymbols(TracingBackend.symbolsToWrite);
        }

        if (bw != null) {
          for (SSymbol s : symbolsToWrite) {
            bw.write(s.getSymbolId() + ":" + s.getString());
            bw.newLine();
          }
          bw.flush();
        }
        TracingBackend.symbolsToWrite.clear();
      }
    }

    private void writeAndRecycleBuffer(final FileOutputStream fos, final ByteBuffer buffer)
        throws IOException {
      if (fos != null) {
        fos.getChannel().write(buffer.getReadingFromStartBuffer());
        fos.flush();
      }
      traceBytes += buffer.position();
      buffer.clear();
      TracingBackend.emptyBuffers.add(buffer);
    }

    private void processTraceData(final FileOutputStream traceDataStream,
        final FileOutputStream externalDataStream,
        final BufferedWriter symbolStream) throws IOException {
      while (cont || !TracingBackend.fullBuffers.isEmpty()) {
        ByteBuffer b = tryToObtainBuffer();
        if (b == null) {
          continue;
        }

        writeExternalData(externalDataStream);
        writeSymbols(symbolStream);

        if (front != null) {
          front.sendTracingData(b);
        }

        writeAndRecycleBuffer(traceDataStream, b);
      }
    }

    @Override
    public void run() {
      File f = new File(VmSettings.TRACE_FILE + ".trace");
      File sf = new File(VmSettings.TRACE_FILE + ".sym");
      File edf = new File(VmSettings.TRACE_FILE + ".dat");
      f.getParentFile().mkdirs();

      try {
        if (VmSettings.DISABLE_TRACE_FILE) {
          processTraceData(null, null, null);
        } else {
          try (FileOutputStream traceDataStream = new FileOutputStream(f);
              FileOutputStream symbolStream = new FileOutputStream(sf);
              FileOutputStream externalDataStream = new FileOutputStream(edf);
              BufferedWriter symbolWriter =
                  new BufferedWriter(new OutputStreamWriter(symbolStream))) {
            processTraceData(traceDataStream, externalDataStream, symbolWriter);
          } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
