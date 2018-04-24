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
import som.vm.VmSettings;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SSymbol;
import tools.concurrency.ActorExecutionTrace.StringWrapper;
import tools.concurrency.ActorExecutionTrace.TwoDArrayWrapper;
import tools.debugger.FrontendConnector;


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
  private static final int BUFFER_POOL_SIZE = VmSettings.NUM_THREADS * 16;
  public static final int  BUFFER_SIZE      = 4096 * 256;

  private static final int TRACE_TIMEOUT = 500;
  private static final int POLL_TIMEOUT  = 50;

  private static final List<GarbageCollectorMXBean> gcbeans =
      ManagementFactory.getGarbageCollectorMXBeans();

  private static final ArrayBlockingQueue<ByteBuffer> emptyBuffers =
      new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);

  private static final ArrayBlockingQueue<ByteBuffer> fullBuffers =
      new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);

  private static final java.util.concurrent.ConcurrentLinkedQueue<Object[]> externalData =
      new java.util.concurrent.ConcurrentLinkedQueue<Object[]>();

  // contains symbols that need to be written to file/sent to debugger,
  // e.g. actor type, message type
  private static final ArrayList<SSymbol> symbolsToWrite =
      new ArrayList<>();

  private static FrontendConnector front = null;

  private static long              collectedMemory = 0;
  private static TraceWorkerThread workerThread    = new TraceWorkerThread();

  static {
    if (VmSettings.MEMORY_TRACING) {
      setUpGCMonitoring();
    }

    if (VmSettings.ACTOR_TRACING || VmSettings.KOMPOS_TRACING) {
      for (int i = 0; i < BUFFER_POOL_SIZE; i++) {
        emptyBuffers.add(ByteBuffer.allocate(BUFFER_SIZE));
      }
    }
  }

  private static long getTotal(final Map<String, MemoryUsage> map) {
    return map.entrySet().stream().mapToLong(usage -> usage.getValue().getUsed()).sum();
  }

  public static void setUpGCMonitoring() {
    for (GarbageCollectorMXBean bean : gcbeans) {
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
  private static void returnBufferGlobally(final ByteBuffer buffer) {
    assert fullBuffers.offer(buffer);
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
    }
  }

  public static final void forceSwapBuffers() {
    assert VmSettings.TRUFFLE_DEBUGGER_ENABLED && VmSettings.KOMPOS_TRACING;
    TracingActivityThread[] result;
    synchronized (tracingThreads) {
      result = tracingThreads.toArray(new TracingActivityThread[0]);
    }

    for (TracingActivityThread t : result) {
      t.swapTracingBuffer = true;
    }

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
        } else if (t instanceof ActorProcessingThread
            && (((ActorProcessingThread) t).getCurrentActor() == null)) {
          // if the thread is not currently having an actor, it is not executing
          runningThreads -= 1;
          result[i] = null;
          t.getBuffer().swapStorage();
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

  @TruffleBoundary
  public static final void addExternalData(final Object[] b) {
    assert b != null;
    externalData.add(b);
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

    private ByteBuffer tryToObtainBuffer() {
      ByteBuffer buffer;
      try {
        buffer = TracingBackend.fullBuffers.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        if (buffer == null) {
          return null;
        } else {
          return buffer;
        }
      } catch (InterruptedException e) {
        return null;
      }
    }

    private void writeExternalData(final FileOutputStream edfos) throws IOException {
      while (!externalData.isEmpty()) {
        Object[] o = externalData.poll();
        for (Object oo : o) {
          if (oo == null) {
            continue;
          }
          if (oo instanceof StringWrapper) {
            StringWrapper sw = (StringWrapper) oo;
            byte[] bytes = sw.s.getBytes();
            byte[] header =
                ActorExecutionTrace.getExtDataHeader(sw.actorId, sw.dataId, bytes.length);

            if (edfos != null) {
              edfos.getChannel().write(java.nio.ByteBuffer.wrap(header));
              edfos.getChannel().write(java.nio.ByteBuffer.wrap(bytes));
              edfos.flush();
            }
            externalBytes += bytes.length + 12;
          } else if (oo instanceof TwoDArrayWrapper) {
            writeArray((TwoDArrayWrapper) oo, edfos);
          } else {

            byte[] data = (byte[]) oo;
            externalBytes += data.length;
            if (edfos != null) {
              edfos.getChannel().write(java.nio.ByteBuffer.wrap(data));
              edfos.flush();
            }
          }
        }
      }
    }

    private void writeArray(final TwoDArrayWrapper aw, final FileOutputStream edfos)
        throws IOException {
      // TODO
      // this is to work around codacy
      edfos.write(aw.actorId);
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
      while (cont || !TracingBackend.fullBuffers.isEmpty()
          || !TracingBackend.externalData.isEmpty()) {
        writeExternalData(externalDataStream);

        ByteBuffer b = tryToObtainBuffer();
        if (b == null) {
          continue;
        }

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

  static final byte STRING = 0;
  static final byte LONG   = 1;
  static final byte DOUBLE = 2;
  static final byte TRUE   = 3;
  static final byte FALSE  = 4;
  static final byte NULL   = 5;
  static final byte ENDROW = 6;

  public static SArray parseArray(final java.nio.ByteBuffer bb) {
    List<SArray> rows = new ArrayList<>();
    List<Object> currentRow = new ArrayList<>();

    while (bb.hasRemaining()) {
      byte type = bb.get();
      if (type == STRING) {
        int len = bb.getInt();
        byte[] bytes = new byte[len];
        bb.get(bytes);
        currentRow.add(new String(bytes));
      } else if (type == LONG) {
        currentRow.add(bb.getLong());
      } else if (type == DOUBLE) {
        currentRow.add(bb.getDouble());
      } else if (type == TRUE) {
        currentRow.add(true);
      } else if (type == FALSE) {
        currentRow.add(false);
      } else if (type == NULL) {
        currentRow.add(Nil.nilObject);
      } else if (type == ENDROW) {
        rows.add(new SImmutableArray(currentRow.toArray(), Classes.arrayClass));
        currentRow.clear();
      }
    }
    return new SImmutableArray(rows.toArray(), Classes.arrayClass);
  }
}
