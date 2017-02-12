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
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
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
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.primitives.TimerPrim;
import som.primitives.processes.ChannelPrimitives.TracingProcess;
import som.vm.ObjectSystem;
import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;
import tools.ObjectBuffer;
import tools.TraceData;
import tools.debugger.FrontendConnector;

public class ActorExecutionTrace {

  private static final int BUFFER_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 4;
  static final int BUFFER_SIZE = 4096 * 1024;

  private static final int MESSAGE_SIZE = 44; // max message size without parameters
  private static final int PARAM_SIZE = 9; // max size for one parameter
  private static final int TRACE_TIMEOUT = 500;
  private static final int POLL_TIMEOUT = 10;

  private static final List<java.lang.management.GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();

  private static final ArrayBlockingQueue<ByteBuffer> emptyBuffers = new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);
  private static final ArrayBlockingQueue<ByteBuffer> fullBuffers  = new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);

  // contains symbols that need to be written to file/sent to debugger,
  // e.g. actor type, message type
  private static final ArrayList<SSymbol> symbolsToWrite = new ArrayList<>();

  private static FrontendConnector front = null;

  private static long collectedMemory = 0;
  private static TraceWorkerThread workerThread = new TraceWorkerThread();
  private static final byte messageEventId;

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
  public static synchronized void swapBuffer(final TracingActivityThread t) throws IllegalStateException {
    returnBuffer(t.getThreadLocalBuffer());

    try {
      t.setThreadLocalBuffer(emptyBuffers.take().put(Events.Thread.id).put((byte) t.getPoolIndex()).putLong(System.currentTimeMillis()));
    } catch (InterruptedException e) {
      throw new IllegalStateException("Failed to acquire a new Buffer!");
    }
  }

  @TruffleBoundary
  public static synchronized void returnBuffer(final ByteBuffer b) {
    if (b == null) {
      return;
    }

    b.limit(b.position());
    b.rewind();

    fullBuffers.add(b);
  }


  protected enum Events {
    ActorCreation(TraceParser.ACTOR_CREATION,         19),
    PromiseCreation(TraceParser.PROMISE_CREATION,     17),
    PromiseResolution(TraceParser.PROMISE_RESOLUTION, 28),
    PromiseChained(TraceParser.PROMISE_CHAINED,       17),
    Mailbox(TraceParser.MAILBOX,                      21),

    // at the beginning of buffer, allows to track what was created/executed
    // on which thread, really cheap solution, timestamp?
    Thread(TraceParser.THREAD, 9),

    // for memory events another buffer is needed
    // (the gc callback is on Thread[Service Thread,9,system])
    MailboxContd(TraceParser.MAILBOX_CONTD,     23),
    BasicMessage(TraceParser.BASIC_MESSAGE,      7),
    PromiseMessage(TraceParser.PROMISE_MESSAGE,  7),

    ProcessCreation(TraceParser.PROCESS_CREATION,     19),
    ProcessCompletion(TraceParser.PROCESS_COMPLETION,  9);

    private final byte id;
    private final int size;

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
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    // don't take buffer from queue, just get reference for briefly
    // adding main actor info
    ByteBuffer b = emptyBuffers.element();
    b.put(Events.ActorCreation.id);
    b.putLong(mainActor.getId()); // id of the created actor
    b.putLong(0); // causal message
    b.putShort(objectSystem.getPlatformClass().getName().getSymbolId());

    // start worker thread for trace processing
    workerThread.start();
  }

  public static void actorCreation(final SFarReference actor) {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;

    TracingActivityThread t = (TracingActivityThread) current;
    if (t.getThreadLocalBuffer().remaining() < Events.ActorCreation.size) {
      swapBuffer(t);
    }

    Object value = actor.getValue();
    assert value instanceof SClass;
    SClass actorClass = (SClass) value;

    ByteBuffer b = t.getThreadLocalBuffer();
    assert b.order() == ByteOrder.BIG_ENDIAN;
    b.put(Events.ActorCreation.id);
    b.putLong(actor.getActor().getId()); // id of the created actor
    b.putLong(t.getCurrentMessageId()); // causal message
    b.putShort(actorClass.getName().getSymbolId());
  }

  public static void processCreation(final TracingProcess proc) {
    assert VmSettings.ACTOR_TRACING;

    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;

    TracingActivityThread t = (TracingActivityThread) current;
    if (t.getThreadLocalBuffer().remaining() < Events.ProcessCreation.size) {
      swapBuffer(t);
    }

    ByteBuffer b = t.getThreadLocalBuffer();
    b.put(Events.ProcessCreation.id);
    b.putLong(proc.getId());
    b.putLong(t.getCurrentMessageId()); // causal message
    b.putShort(proc.getProcObject().getSOMClass().getName().getSymbolId());
  }

  public static void processCompletion(final TracingProcess proc) {
    assert VmSettings.ACTOR_TRACING;

    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;

    TracingActivityThread t = (TracingActivityThread) current;
    if (t.getThreadLocalBuffer().remaining() < Events.ProcessCompletion.size) {
      swapBuffer(t);
    }

    ByteBuffer b = t.getThreadLocalBuffer();
    b.put(Events.ProcessCompletion.id);
    b.putLong(proc.getId());
  }

  public static void promiseCreation(final long promiseId) {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    TracingActivityThread t = (TracingActivityThread) current;

    if (t.getThreadLocalBuffer().remaining() < Events.PromiseCreation.size) {
      swapBuffer(t);
    }

    ByteBuffer b = t.getThreadLocalBuffer();
    b.put(Events.PromiseCreation.id);
    b.putLong(promiseId); // id of the created promise
    b.putLong(t.getCurrentMessageId()); // causal message
  }

  public static void promiseResolution(final long promiseId, final Object value) {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    Thread current = Thread.currentThread();
    if (TimerPrim.isTimerThread(current)) {
      return;
    }

    assert current instanceof TracingActivityThread;
    TracingActivityThread t = (TracingActivityThread) current;

    if (t.getThreadLocalBuffer().remaining() < Events.PromiseResolution.size) {
      swapBuffer(t);
    }

    ByteBuffer b = t.getThreadLocalBuffer();
    b.put(Events.PromiseResolution.id);
    b.putLong(promiseId); // id of the promise
    b.putLong(t.getCurrentMessageId()); // resolving message
    writeParameter(value, b);

    t.resolvedPromises++;
  }

  public static void promiseChained(final long parent, final long child) {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    TracingActivityThread t = (TracingActivityThread) current;

    if (t.getThreadLocalBuffer().remaining() < Events.PromiseChained.size) {
      swapBuffer(t);
    }

    ByteBuffer b = t.getThreadLocalBuffer();
    b.put(Events.PromiseChained.id);
    b.putLong(parent); // id of the parent
    b.putLong(child); // id of the chained promise
    t.resolvedPromises++;
  }

  public static void mailboxExecuted(final EventualMessage m,
      final ObjectBuffer<EventualMessage> moreCurrent, final long baseMessageId, final int mailboxNo, final long sendTS,
      final ObjectBuffer<Long> moreSendTS, final long[] execTS, final Actor actor) {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    TracingActivityThread t = (TracingActivityThread) current;

    if (t.getThreadLocalBuffer().remaining() < Events.Mailbox.size + 100 * 50) {
      swapBuffer(t);
    }

    ByteBuffer b = t.getThreadLocalBuffer();
    b.put(Events.Mailbox.id);
    b.putLong(baseMessageId); // base id for messages
    b.putInt(mailboxNo);
    b.putLong(actor.getId());   // receiver of the messages

    int idx = 0;

    if (b.remaining() < (MESSAGE_SIZE + m.getArgs().length * PARAM_SIZE)) {
      swapBuffer(t);
      b = t.getThreadLocalBuffer();
      b.put(Events.MailboxContd.id);
      b.putLong(baseMessageId);
      b.putInt(mailboxNo);
      b.putLong(actor.getId()); // receiver of the messages
      b.putInt(idx);
    }

    writeBasicMessage(m, b);

    if (VmSettings.MESSAGE_TIMESTAMPS) {
      b.putLong(execTS[0]);
      b.putLong(sendTS);
    }

    if (VmSettings.MESSAGE_PARAMETERS) {
      writeParameters(m.getArgs(), b);
    }
    idx++;

    if (moreCurrent != null) {
      Iterator<Long> it = null;
      if (VmSettings.MESSAGE_TIMESTAMPS) {
        assert moreSendTS != null && moreCurrent.size() == moreSendTS.size();
        it = moreSendTS.iterator();
      }
      for (EventualMessage em : moreCurrent) {
        if (b.remaining() < (MESSAGE_SIZE + em.getArgs().length * PARAM_SIZE)) {
          swapBuffer(t);
          b = t.getThreadLocalBuffer();
          b.put(Events.MailboxContd.id);
          b.putLong(baseMessageId);
          b.putInt(mailboxNo);
          b.putLong(actor.getId()); // receiver of the messages
          b.putInt(idx);
        }

        writeBasicMessage(em, b);

        if (VmSettings.MESSAGE_TIMESTAMPS) {
          b.putLong(execTS[idx]);
          b.putLong(it.next());
        }

        if (VmSettings.MESSAGE_PARAMETERS) {
          writeParameters(em.getArgs(), b);
        }
        idx++;
      }
    }
  }

  private static void writeBasicMessage(final EventualMessage em, final ByteBuffer b) {
    if (em instanceof PromiseMessage && VmSettings.PROMISE_CREATION) {
      b.put((byte) (messageEventId | TraceData.PROMISE_BIT));
      b.putLong(((PromiseMessage) em).getPromise().getPromiseId());
    } else {
      b.put(messageEventId);
    }

    b.putLong(em.getSender().getId()); // sender
    b.putLong(em.getCausalMessageId());

    b.putShort(em.getSelector().getSymbolId());
  }

  private static void writeParameters(final Object[] params, final ByteBuffer b) {
    b.put((byte) (params.length - 1)); // num paramaters

    for (int i = 1; i < params.length; i++) {
      // will need a 8 plus 1 byte for most parameter,
      // boolean just use two identifiers.
      if (params[i] instanceof SFarReference) {
        Object o = ((SFarReference) params[i]).getValue();
        writeParameter(o, b);
      } else {
        writeParameter(params[i], b);
      }
    }
  }

  private static void writeParameter(final Object param, final ByteBuffer b) {
    if (param instanceof SPromise) {
      b.put(ParamTypes.Promise.id());
      b.putLong(((SPromise) param).getPromiseId());
    } else if (param instanceof SResolver) {
      b.put(ParamTypes.Resolver.id());
      b.putLong(((SResolver) param).getPromise().getPromiseId());
    } else if (param instanceof SAbstractObject) {
      b.put(ParamTypes.Object.id());
      b.putShort(((SAbstractObject) param).getSOMClass().getName().getSymbolId());
    } else {
      if (param instanceof Long) {
        b.put(ParamTypes.Long.id());
        b.putLong((Long) param);
      } else if (param instanceof Double) {
        b.put(ParamTypes.Double.id());
        b.putDouble((Double) param);
      } else if (param instanceof Boolean) {
        if ((Boolean) param) {
          b.put(ParamTypes.True.id());
        } else {
          b.put(ParamTypes.False.id());
        }
      } else if (param instanceof String) {
        b.put(ParamTypes.String.id());
      } else {
        throw new RuntimeException("unexpected parameter type");
      }
      // TODO add case for null/nil/exception,
      // ask ctorresl about what type is used for the error handling stuff
    }
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
      final long baseMessageId, final int mailboxNo, final Actor actor) {
    Thread current = Thread.currentThread();

    assert current instanceof TracingActivityThread;
    TracingActivityThread t = (TracingActivityThread) current;

    if (t.getThreadLocalBuffer().remaining() < Events.Mailbox.size + 100 * 50) {
      swapBuffer(t);
    }

    ByteBuffer b = t.getThreadLocalBuffer();
    b.put(Events.Mailbox.id);
    b.putLong(baseMessageId); // base id for messages
    b.putInt(mailboxNo);
    b.putLong(actor.getId());    // receiver of the messages

    int idx = 0;

    if (todo != null) {
      for (EventualMessage em : todo) {
        if (b.remaining() < (MESSAGE_SIZE + em.getArgs().length * PARAM_SIZE)) {
          swapBuffer(t);
          b = t.getThreadLocalBuffer();
          b.put(Events.MailboxContd.id);
          b.putLong(baseMessageId);
          b.putLong(actor.getId()); // receiver of the messages
          b.putInt(idx);
        }

        writeBasicMessage(em, b);

        if (VmSettings.MESSAGE_PARAMETERS) {
          writeParameters(em.getArgs(), b);
        }
        idx++;
      }
    }
  }
}
