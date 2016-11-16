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
import java.util.Date;
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
import som.interpreter.actors.Mailbox;
import som.interpreter.actors.SFarReference;
import som.vm.ObjectSystem;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;
import tools.debugger.FrontendConnector;

public class ActorExecutionTrace {

  private static final int MSG_BUFFER_SIZE = 128;
  private static final int BUFFER_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 4;
  private static final int BUFFER_SIZE = 4096 * 1024;

  private static final MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
  private static final List<java.lang.management.GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();

  private static final ArrayBlockingQueue<ByteBuffer> emptyBuffers = new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);
  private static final ArrayBlockingQueue<ByteBuffer> fullBuffers = new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);

  //contains symbols that need to be written to file/sent to debugger, e.g. actor type, message type
  private static final ArrayList<SSymbol> symbolsToWrite = new ArrayList<>();

  private static long actorSystemStartTime;
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
      actorSystemStartTime = System.currentTimeMillis();
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
    PromiseResolution((byte) 3, 17),
    PromiseChained((byte) 4, 17),
    Mailbox((byte) 5, 19), //plus contained messages
    Thread((byte) 6, 9); //at the beginning of buffer, allows to track what was created/executed on which thread, really cheap solution, timestamp?
    //for memory events another buffer is needed (the gc callback is on Thread[Service Thread,9,system])

    private final byte id;
    private final int size;

    Events(final byte id, final int size) {
      this.id = id;
      this.size = size;
    }
  };

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

  public static void promiseResolution(final long promiseId) {
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
      //resolved with
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

      if (t.getThreadLocalBuffer().remaining() < Events.Mailbox.size + m.size() * 18) {
        swapBuffer(t);
      }
      ByteBuffer b = t.getThreadLocalBuffer();
      b.put(Events.Mailbox.id);
      b.putShort((short) m.size()); //number of messages in the mailbox. enough??
      b.putLong(m.getBasemessageId()); //base id for messages
      b.putLong(actor.getActorId()); //receiver of the messages
      for (EventualMessage em : m) {
        b.putLong(em.getSender().getActorId()); // sender
        b.putLong(em.getCausalMessageId());
        b.putShort(em.getSelector().getSymbolId());
      }
    }

    logMemoryUsage();
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
      File folder = new File(System.getProperty("user.dir") + "/traces");
      folder.mkdir();

      File f = new File(folder.getAbsolutePath() + "/" + new Date().toString() + ".trace");
      File sf = new File(folder.getAbsolutePath() + "/" + new Date().toString() + ".sym");

      try (FileOutputStream fos = new FileOutputStream(f);
          FileOutputStream sfos = new FileOutputStream(sf);
          BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(sfos))) {


        while (true) {
          ByteBuffer b = ActorExecutionTrace.fullBuffers.take();

          fos.getChannel().write(b);
          b.rewind();

          if (front != null) {
            synchronized (symbolsToWrite) {
              front.sendSymbols(ActorExecutionTrace.symbolsToWrite);
            }

            front.sendTracingData(b);
          }

          synchronized (symbolsToWrite) {
            for (SSymbol s : symbolsToWrite) {
              bw.write(s.getSymbolId() + ":" + s.getString());
              bw.newLine();
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
