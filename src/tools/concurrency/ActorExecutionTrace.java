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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.sun.management.GarbageCollectionNotificationInfo;

import som.VM;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.primitives.TimerPrim;
import som.primitives.processes.ChannelPrimitives.TracingProcess;
import som.vm.ObjectSystem;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.ObjectBuffer;
import tools.TraceData;
import tools.debugger.FrontendConnector;


/**
 * Tracing of the execution is done on a per-thread basis to minimize overhead
 * at run-time.
 *
 * <p>To communicate detailed information about the system for debugging,
 * TraceWorkerThreads will capture the available data periodically and send it
 * to the debugger.
 *
 * <h4>Synchronization Strategy</h4>
 * <p>During normal execution with tracing, we do not need any synchronization,
 * because all operations on the buffers are initiated by the
 * TracingActivityThreads themselves. Thus, everything is thread-local. The
 * swapping of buffers is initiated from the TracingActivityThreads, too.
 * It requires synchronization only on the data structure where the buffers
 * are listed.
 *
 * <p>During execution with the debugger however, the periodic data requests
 * cause higher synchronization requirements. Since this is about interactive
 * debugging the cost of synchronization is likely acceptable, and will simply
 * done on the buffer for all access.
 */
public class ActorExecutionTrace {
  private static final int BUFFER_POOL_SIZE = VmSettings.NUM_THREADS * 4;
  static final int BUFFER_SIZE = 4096 * 1024;

  static final int MESSAGE_SIZE = 44; // max message size without parameters
  static final int PARAM_SIZE = 9; // max size for one parameter
  private static final int TRACE_TIMEOUT = 500;
  private static final int POLL_TIMEOUT  = 50;

  private static final List<java.lang.management.GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();

  private static final ArrayBlockingQueue<ByteBuffer> emptyBuffers = new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);
  private static final ArrayBlockingQueue<ByteBuffer> fullBuffers  = new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);

  // contains symbols that need to be written to file/sent to debugger,
  // e.g. actor type, message type
  private static final ArrayList<SSymbol> symbolsToWrite = new ArrayList<>();

  private static FrontendConnector front = null;

  private static long collectedMemory = 0;
  private static TraceWorkerThread workerThread = new TraceWorkerThread();
  static final byte messageEventId;

  static {
    if (VmSettings.MEMORY_TRACING) {
      setUpGCMonitoring();
    }

    if (VmSettings.ACTOR_TRACING) {
      for (int i = 0; i < BUFFER_POOL_SIZE; i++) {
        emptyBuffers.add(ByteBuffer.allocate(BUFFER_SIZE));
      }
    }

    byte eventid = TraceData.MESSAGE_BASE;

    if (VmSettings.MESSAGE_TIMESTAMPS) {
      eventid |= TraceData.TIMESTAMP_BIT;
    }

    if (VmSettings.MESSAGE_PARAMETERS) {
      eventid |= TraceData.PARAMETER_BIT;
    }

    messageEventId = eventid;
  }

  private static long getTotal(final Map<String, MemoryUsage> map) {
    return map.entrySet().stream().
        mapToLong(usage -> usage.getValue().getUsed()).sum();
  }

  public static void setUpGCMonitoring() {
    for (java.lang.management.GarbageCollectorMXBean bean : gcbeans) {
      NotificationEmitter emitter = (NotificationEmitter) bean;
      NotificationListener listener = new NotificationListener() {
        @Override
        public void handleNotification(final Notification notification, final Object handback) {
          if (GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from(
                (CompositeData) notification.getUserData());
            long after  = getTotal(info.getGcInfo().getMemoryUsageAfterGc());
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
    VM.println("[Memstat] Heap: " + totalHeap + "B\tNonHeap: " + totalNonHeap + "B\tCollected: " + collectedMemory + "B\tGC-Time: " + gcTime + "ms");
  }

  @TruffleBoundary
  static synchronized ByteBuffer getEmptyBuffer() {
    try {
      return emptyBuffers.take();
    } catch (InterruptedException e) {
      throw new IllegalStateException("Failed to acquire a new Buffer!");
    }
  }

  @TruffleBoundary
  static synchronized void returnBuffer(final ByteBuffer b) {
    if (b == null) {
      return;
    }

    b.limit(b.position());
    b.rewind();

    fullBuffers.add(b);
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
    assert VmSettings.TRUFFLE_DEBUGGER_ENABLED && VmSettings.ACTOR_TRACING;
    TracingActivityThread[] result;
    synchronized (tracingThreads) {
      result = tracingThreads.toArray(new TracingActivityThread[0]);
    }

    for (TracingActivityThread t : result) {
      TraceBuffer buffer = t.getBuffer();
      synchronized (buffer) {
        buffer.swapStorage();
      }
    }
  }

  protected enum Events {
    ActorCreation(TraceParser.ACTOR_CREATION,         19),
    PromiseCreation(TraceParser.PROMISE_CREATION,     17),
    PromiseResolution(TraceParser.PROMISE_RESOLUTION, 28),
    PromiseChained(TraceParser.PROMISE_CHAINED,       17),
    Mailbox(TraceParser.MAILBOX,                      21),

    // at the beginning of buffer, allows to track what was created/executed
    // on which thread, really cheap solution, timestamp?
    Thread(TraceParser.THREAD, 17),

    // for memory events another buffer is needed
    // (the gc callback is on Thread[Service Thread,9,system])
    MailboxContd(TraceParser.MAILBOX_CONTD,     25),
    BasicMessage(TraceParser.BASIC_MESSAGE,      7),
    PromiseMessage(TraceParser.PROMISE_MESSAGE,  7),

    ProcessCreation(TraceParser.PROCESS_CREATION,     19),
    ProcessCompletion(TraceParser.PROCESS_COMPLETION,  9),

    TaskSpawn(TraceParser.TASK_SPAWN, 3),
    TaskJoin(TraceParser.TASK_JOIN,   3);

    final byte id;
    final int size;

    Events(final byte id, final int size) {
      this.id = id;
      this.size = size;
    }
  };

  protected enum ParamTypes {
    False,
    True,
    Long,
    Double,
    Promise,
    Resolver,
    Object,
    String;

    byte id() {
      return (byte) this.ordinal();
    }
  }

  public static void recordMainActor(final Actor mainActor,
      final ObjectSystem objectSystem) {
    ByteBuffer storage = getEmptyBuffer();
    TraceBuffer buffer = TraceBuffer.create();
    buffer.init(storage, 0);

    buffer.recordMainActor(mainActor, objectSystem);
    buffer.returnBuffer();

    // start worker thread for trace processing
    workerThread.start();
  }

  public static void actorCreation(final SFarReference actor) {
    TracingActivityThread t = getThread();
    t.getBuffer().recordActorCreation(actor, t.getCurrentMessageId());
  }

  public static void processCreation(final TracingProcess proc) {
    TracingActivityThread t = getThread();
    t.getBuffer().recordProcessCreation(proc, t.getCurrentMessageId());
  }

  public static void processCompletion(final TracingProcess proc) {
    TracingActivityThread t = getThread();
    t.getBuffer().recordProcessCompletion(proc);
  }

  public static void taskSpawn(final SInvokable method) {
    TracingActivityThread t = getThread();
    t.getBuffer().recordTaskSpawn(method);
  }

  public static void taskJoin(final SInvokable method) {
    TracingActivityThread t = getThread();
    t.getBuffer().recordTaskJoin(method);
  }

  public static void promiseCreation(final long promiseId) {
    TracingActivityThread t = getThread();
    t.getBuffer().recordPromiseCreation(promiseId, t.getCurrentMessageId());
  }

  public static void promiseResolution(final long promiseId, final Object value) {
    Thread current = Thread.currentThread();
    if (TimerPrim.isTimerThread(current)) {
      return;
    }

    assert current instanceof TracingActivityThread;
    TracingActivityThread t = (TracingActivityThread) current;

    t.getBuffer().recordPromiseResolution(promiseId, value, t.getCurrentMessageId());

    t.resolvedPromises++;
  }

  public static void promiseChained(final long parent, final long child) {
    TracingActivityThread t = getThread();
    t.getBuffer().recordPromiseChained(parent, child);
    t.resolvedPromises++;
  }

  public static void mailboxExecuted(final EventualMessage m,
      final ObjectBuffer<EventualMessage> moreCurrent, final long baseMessageId,
      final int mailboxNo, final long sendTS, final ObjectBuffer<Long> moreSendTS,
      final long[] execTS, final Actor receiver) {
    TracingActivityThread t = getThread();
    t.getBuffer().recordMailboxExecuted(m, moreCurrent, baseMessageId,
        mailboxNo, sendTS, moreSendTS, execTS, receiver);
  }

  private static TracingActivityThread getThread() {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    return (TracingActivityThread) current;
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

    @Override
    public void run() {

      File f = new File(VmSettings.TRACE_FILE + ".trace");
      File sf = new File(VmSettings.TRACE_FILE + ".sym");
      f.getParentFile().mkdirs();

      if (!VmSettings.DISABLE_TRACE_FILE) {
        try (FileOutputStream fos = new FileOutputStream(f);
            FileOutputStream sfos = new FileOutputStream(sf);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(sfos))) {

          while (cont || ActorExecutionTrace.fullBuffers.size() > 0) {
            ByteBuffer b;

            try {
              b = ActorExecutionTrace.fullBuffers.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
              if (b == null) {
                continue;
              }
            } catch (InterruptedException e) {
              continue;
            }

            synchronized (symbolsToWrite) {
              if (front != null) {
                front.sendSymbols(ActorExecutionTrace.symbolsToWrite);
              }

              for (SSymbol s : symbolsToWrite) {
                bw.write(s.getSymbolId() + ":" + s.getString());
                bw.newLine();
              }
              bw.flush();
              ActorExecutionTrace.symbolsToWrite.clear();
            }

            fos.getChannel().write(b);
            fos.flush();
            b.rewind();

            if (front != null) {
              front.sendTracingData(b);
            }

            b.clear();
            ActorExecutionTrace.emptyBuffers.add(b);
          }
        } catch (FileNotFoundException e) {
          throw new RuntimeException(e);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        while (cont || ActorExecutionTrace.fullBuffers.size() > 0) {
          ByteBuffer b;

          try {
            b = ActorExecutionTrace.fullBuffers.poll(POLL_TIMEOUT, TimeUnit.MILLISECONDS);
            if (b == null) {
              continue;
            }
          } catch (InterruptedException e) {
            continue;
          }

          synchronized (symbolsToWrite) {
            if (front != null) {
              front.sendSymbols(ActorExecutionTrace.symbolsToWrite);
            }
            ActorExecutionTrace.symbolsToWrite.clear();
          }

          if (front != null) {
            front.sendTracingData(b);
          }

          b.clear();
          ActorExecutionTrace.emptyBuffers.add(b);
        }
      }
    }
  }

  public static void mailboxExecutedReplay(final Queue<EventualMessage> todo,
      final long baseMessageId, final int mailboxNo, final Actor receiver) {
    TracingActivityThread t = getThread();
    t.getBuffer().recordMailboxExecutedReplay(todo, baseMessageId, mailboxNo, receiver);
  }
}
