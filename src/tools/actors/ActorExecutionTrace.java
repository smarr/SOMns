package tools.actors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.sun.management.GarbageCollectionNotificationInfo;

import som.VM;
import som.VmSettings;
import som.interpreter.actors.Actor;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.interpreter.actors.Mailbox;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.ObjectSystem;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;
import tools.debugger.FrontendConnector;

public class ActorExecutionTrace {

  private static final int BUFFER_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 4;
  private static final int BUFFER_SIZE = 4096 * 1024;

  private static final MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
  private static final List<java.lang.management.GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();

  private static final ArrayBlockingQueue<ByteBuffer> emptyBuffers = new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);
  private static final ArrayBlockingQueue<ByteBuffer> fullBuffers = new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);

  //contains symbols that need to be written to file/sent to debugger, e.g. actor type, message type
  private static final ArrayList<SSymbol> symbolsToWrite = new ArrayList<>();

  private static FrontendConnector front = null;

  private static Thread workerThread = new TraceWorkerThread();

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

  public static void recordMainActor(final Actor mainActor,
      final ObjectSystem objectSystem) {
    if (VmSettings.ACTOR_TRACING) {
      workerThread.start();
    }
  }

  public static void setUpGCMonitoring() {
    for (java.lang.management.GarbageCollectorMXBean bean : gcbeans) {
      NotificationEmitter emitter = (NotificationEmitter) bean;
      NotificationListener listener = new NotificationListener() {
        @Override
        public void handleNotification(final Notification notification, final Object handback) {
          if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
            //get the information associated with this notification
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
            VM.println(Thread.currentThread().toString());
            VM.println(info.getGcAction() + ": - " + info.getGcInfo().getId() + " " + info.getGcName() + " (from " + info.getGcCause() + ") " + info.getGcInfo().getDuration() + " ms;");
            VM.println("GcInfo MemoryUsageBeforeGc: " + info.getGcInfo().getMemoryUsageBeforeGc().entrySet().stream().filter(ent -> !ent.getKey().equals("Compressed Class Space") && !ent.getKey().equals("Code Cache")).mapToLong(usage -> usage.getValue().getUsed()).sum() / 1024 + " kB");
            VM.println("GcInfo MemoryUsageAfterGc: " + info.getGcInfo().getMemoryUsageAfterGc().entrySet().stream().filter(ent -> !ent.getKey().equals("Compressed Class Space") && !ent.getKey().equals("Code Cache")).mapToLong(usage -> usage.getValue().getUsed()).sum() / 1024 + " kB");
          }
        }
      };

      emitter.addNotificationListener(listener, null, null);
    }
  }


  public static void logMemoryUsage() {
    if (VmSettings.MEMORY_TRACING) {
      VM.println("Memory usage: " + mbean.getHeapMemoryUsage().getUsed() / 1024 + " kB");
    }
  }

  @TruffleBoundary
  public static synchronized void swapBuffer(final ActorProcessingThread t) throws IllegalStateException {
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
    ActorCreation((byte) 1, 19),
    PromiseCreation((byte) 2, 17),
    PromiseResolution((byte) 3, 28),
    PromiseChained((byte) 4, 17),
    Mailbox((byte) 5, 17),

    Thread((byte) 6, 9), //at the beginning of buffer, allows to track what was created/executed on which thread, really cheap solution, timestamp?
    //for memory events another buffer is needed (the gc callback is on Thread[Service Thread,9,system])
    MailboxContd((byte) 7, 19),
    BasicMessage((byte) 8, 7),
    PromiseMessage((byte) 9, 7);

    private final byte id;
    private final int size;

    Events(final byte id, final int size) {
      this.id = id;
      this.size = size;
    }
  };

  protected enum ParamTypes{
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

  public static void actorCreation(final SFarReference actor) {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    Thread current = Thread.currentThread();

    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;
      if (t.getThreadLocalBuffer().remaining() < Events.ActorCreation.size) {
        swapBuffer(t);
      }

      Object value = actor.getValue();
      assert value instanceof SClass;
      SClass actorClass = (SClass) value;

      ByteBuffer b = t.getThreadLocalBuffer();
      b.put(Events.ActorCreation.id);
      b.putLong(actor.getActor().getActorId()); // id of the created actor
      b.putLong(t.getCurrentMessageId()); // causal message
      b.putShort(actorClass.getName().getSymbolId());
    }
  }

  public static void promiseCreation(final long promiseId) {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    Thread current = Thread.currentThread();

    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;

      if (t.getThreadLocalBuffer().remaining() < Events.PromiseCreation.size) {
        swapBuffer(t);
      }

      ByteBuffer b = t.getThreadLocalBuffer();
      b.put(Events.PromiseCreation.id);
      b.putLong(promiseId); // id of the created promise
      b.putLong(t.getCurrentMessageId()); // causal message
    }
  }

  public static void promiseResolution(final long promiseId, final Object value) {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    Thread current = Thread.currentThread();

    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;

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
  }

  public static void promiseChained(final long parent, final long child) {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    Thread current = Thread.currentThread();

    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;

      if (t.getThreadLocalBuffer().remaining() < Events.PromiseChained.size) {
        swapBuffer(t);
      }

      ByteBuffer b = t.getThreadLocalBuffer();
      b.put(Events.PromiseChained.id);
      b.putLong(parent); // id of the parent
      b.putLong(child); // id of the chained promise
      t.resolvedPromises++;
    }
  }

  public static void mailboxExecuted(final Mailbox m, final Actor actor) {
    if (!VmSettings.ACTOR_TRACING) {
      return;
    }

    Thread current = Thread.currentThread();

    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;

      if (t.getThreadLocalBuffer().remaining() < Events.Mailbox.size + 100 * 50) {
        swapBuffer(t);
      }

      ByteBuffer b = t.getThreadLocalBuffer();
      b.put(Events.Mailbox.id);
      b.putLong(m.getBasemessageId()); //base id for messages
      b.putLong(actor.getActorId()); //receiver of the messages
      int idx = 0;

      for (EventualMessage em : m) {
        if (b.remaining() < (45 + em.getArgs().length * 9)) {
          swapBuffer(t);
          b = t.getThreadLocalBuffer();
          b.put(Events.MailboxContd.id);
          b.putLong(m.getBasemessageId());
          b.putLong(actor.getActorId()); //receiver of the messages
          b.putShort((short) idx);
        }

        if (em instanceof PromiseSendMessage) {
          b.put(Events.PromiseMessage.id);
          b.putLong(((PromiseMessage) em).getPromise().getPromiseId());
        } else {
          b.put(Events.BasicMessage.id);
        }

        b.putLong(em.getSender().getActorId()); // sender
        b.putLong(em.getCausalMessageId());
        b.putShort(em.getSelector().getSymbolId());
        b.putLong(m.getMessageExecutionStart(idx));
        b.putLong(m.getMessageSendTime(idx));

        Object[] args = em.getArgs();
        b.put((byte) (args.length - 1)); //num paramaters

        for (int i = 1; i < args.length; i++) {
          //gonna need a 8 plus 1 byte for most parameter, boolean just use two identifiers.
          if (args[i] instanceof SFarReference) {
            Object o = ((SFarReference) args[i]).getValue();
            writeParameter(o, b);
          } else {
            writeParameter(args[i], b);
          }
        }

        idx++;
      }
    }

    logMemoryUsage();
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
    }
  }

  /**
   *
   * @param fc The FrontendConnector used to send data to the debugger.
   */
  public static void setFrontEnd(final FrontendConnector fc) {
    front = fc;
  }

  public static synchronized void logSymbol(final SSymbol symbol) {
    symbolsToWrite.add(symbol);
  }

  private static class TraceWorkerThread extends Thread{
    @Override
    public void run() {
      File f = new File(VmSettings.TRACE_FILE + ".trace");
      File sf = new File(VmSettings.TRACE_FILE + ".sym");
      f.getParentFile().mkdirs();

      try (FileOutputStream fos = new FileOutputStream(f);
          FileOutputStream sfos = new FileOutputStream(sf);
          BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(sfos))) {


        while (true) {
          ByteBuffer b = ActorExecutionTrace.fullBuffers.take();

          if (!VmSettings.DISABLE_TRACE_FILE) {
            fos.getChannel().write(b);
            b.rewind();
          }

          if (front != null) {
            front.sendTracingData(b);
          }

          synchronized (symbolsToWrite) {
            if (front != null) {
              front.sendSymbols(ActorExecutionTrace.symbolsToWrite);
            }

            if (!VmSettings.DISABLE_TRACE_FILE) {
              for (SSymbol s : symbolsToWrite) {
                bw.write(s.getSymbolId() + ":" + s.getString());
                bw.newLine();
              }
            }

            ActorExecutionTrace.symbolsToWrite.clear();
          }

          b.clear();
          ActorExecutionTrace.emptyBuffers.add(b);
        }
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      } catch (IOException e) {
        throw new RuntimeException(e);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
