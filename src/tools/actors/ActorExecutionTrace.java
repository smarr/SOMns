package tools.actors;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;

import som.VmSettings;
import som.interpreter.actors.Actor;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.Actor.Mailbox;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.vm.ObjectSystem;
import tools.ObjectBuffer;
import tools.debugger.message.Message;

public class ActorExecutionTrace {

  private static final int MSG_BUFFER_SIZE = 128;
  private static final int BUFFER_POOL_SIZE = Runtime.getRuntime().availableProcessors()*4;

  /** Access to this data structure needs to be synchronized. */
  private static final ObjectBuffer<ObjectBuffer<SFarReference>> createdActorsPerThread =
      VmSettings.ACTOR_TRACING ? new ObjectBuffer<>(VmSettings.NUM_THREADS) : null;

  /** Access to this data structure needs to be synchronized. Typically via {@link createdActorsPerThread} */
  private static final ObjectBuffer<ObjectBuffer<Mailbox>> messagesProcessedPerThread =
      VmSettings.ACTOR_TRACING ? new ObjectBuffer<>(VmSettings.NUM_THREADS) : null;

  private static final ObjectBuffer<ObjectBuffer<Message>> messagesPerThread =
      VmSettings.ACTOR_TRACING ? new ObjectBuffer<>(VmSettings.NUM_THREADS) : null;

  private static final MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
  private static final List<java.lang.management.GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();

  private static final ArrayBlockingQueue<ByteBuffer> bufferPool = new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);
  private static final ArrayBlockingQueue<ByteBuffer> writerQueue = new ArrayBlockingQueue<ByteBuffer>(BUFFER_POOL_SIZE);


  private static long actorSystemStartTime;

  public static ObjectBuffer<ObjectBuffer<SFarReference>> getAllCreateActors() {
    return createdActorsPerThread;
  }

  public static ObjectBuffer<ObjectBuffer<Mailbox>> getAllProcessedMessages() {
    return messagesProcessedPerThread;
  }

  public static ObjectBuffer<ObjectBuffer<Message>> getMessagesperthread() {
    return messagesPerThread;
  }


  static{
    if(VmSettings.MEMORY_TRACING){
      setUpGCMonitoring();
    }

    if(VmSettings.ACTOR_TRACING){
      for(int i = 0; i < BUFFER_POOL_SIZE; i++){
        bufferPool.add(ByteBuffer.allocate(4096*1024));
      }

      Thread workerThread = new Thread(new Runnable() {
        @Override
        public void run() {
          System.out.println("Worker ready!");
          try {
            while(true){
              ByteBuffer b = ActorExecutionTrace.writerQueue.take();
              //TODO send/write data
              System.out.println("Buffer Taken" + b.position());
              while(b.hasRemaining()){
                switch(b.get()){
                  case 1 :
                    System.out.println("[ACTOR] A" + b.getLong() + " M" + b.getLong());
                    break;
                  case 2 :
                    System.out.println("[PROMISE] P" + b.getLong() + " M" + b.getLong());
                    break;
                  case 3 :
                    System.out.println("[RESOLVED] P" + b.getLong() + " M" + b.getLong());
                    break;
                  case 4 :
                    System.out.println("[CHAINED] P" + b.getLong() + " P" + b.getLong());
                    break;
                  case 5 :
                    short num = b.getShort();
                    long baseid = b.getLong();
                    long receiver = b.getLong();
                    System.out.println("[MAILBOX] of A"+receiver);
                    for(int i = 0; i < num; i++){
                      System.out.println("\tM" + (baseid+i)+ " from A" +b.getLong());
                    }
                    break;
                  default:
                    System.out.println("Event not supported");
                }
              }
              System.out.println("done");
              b.clear();
              ActorExecutionTrace.bufferPool.put(b);
            }
          } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
      });
      workerThread.start();
    }
  }

  public static void recordMainActor(final Actor mainActor,
      final ObjectSystem objectSystem) {
    if (VmSettings.ACTOR_TRACING) {
      actorSystemStartTime = System.currentTimeMillis();
      ObjectBuffer<ObjectBuffer<SFarReference>> actors = getAllCreateActors();
      SFarReference mainActorRef = new SFarReference(mainActor,
          objectSystem.getPlatformClass());

      ObjectBuffer<SFarReference> main = new ObjectBuffer<>(1);
      main.append(mainActorRef);
      actors.append(main);
    }
  }

  public static void setUpGCMonitoring(){
    for(java.lang.management.GarbageCollectorMXBean bean : gcbeans){
      NotificationEmitter emitter = (NotificationEmitter) bean;
      NotificationListener listener = new NotificationListener() {
        @Override
        public void handleNotification(final Notification notification, final Object handback) {
          if (notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
            //get the information associated with this notification
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());

            System.out.println(Thread.currentThread().toString());
            System.out.println(info.getGcAction() + ": - " + info.getGcInfo().getId()+ " " + info.getGcName() + " (from " + info.getGcCause()+") "+ info.getGcInfo().getDuration() + " ms;");
            System.out.println("GcInfo MemoryUsageBeforeGc: " + info.getGcInfo().getMemoryUsageBeforeGc().entrySet().stream().filter(ent -> !ent.getKey().equals("Compressed Class Space") && !ent.getKey().equals("Code Cache")).mapToLong(usage -> usage.getValue().getUsed()).sum()/1024 + " kB");
            System.out.println("GcInfo MemoryUsageAfterGc: " + info.getGcInfo().getMemoryUsageAfterGc().entrySet().stream().filter(ent -> !ent.getKey().equals("Compressed Class Space") && !ent.getKey().equals("Code Cache")).mapToLong(usage -> usage.getValue().getUsed()).sum()/1024 + " kB");

          }
        }
      };

      emitter.addNotificationListener(listener, null, null);
    }
  }

  public static void logMemoryUsage(){
    if(VmSettings.MEMORY_TRACING){
      System.out.println("Current Memory usage: " + mbean.getHeapMemoryUsage().getUsed() /1024 + " kB");
    }
  }

  public static ObjectBuffer<SFarReference> createActorBuffer() {
    ObjectBuffer<SFarReference> createdActors;

    if (VmSettings.ACTOR_TRACING) {
      createdActors = new ObjectBuffer<>(MSG_BUFFER_SIZE);

      ObjectBuffer<ObjectBuffer<SFarReference>> createdActorsPerThread = getAllCreateActors();

      // publish the thread local buffer for later querying
      synchronized (createdActorsPerThread) {
        createdActorsPerThread.append(createdActors);
      }
    } else {
      createdActors = null;
    }
    return createdActors;
  }

  public static ObjectBuffer<Mailbox> createProcessedMessagesBuffer() {
    ObjectBuffer<Mailbox> processedMessages;

    if (VmSettings.ACTOR_TRACING) {
      processedMessages = new ObjectBuffer<>(MSG_BUFFER_SIZE);

      ObjectBuffer<ObjectBuffer<Mailbox>> messagesProcessedPerThread = getAllProcessedMessages();

      // publish the thread local buffer for later querying
      synchronized (messagesProcessedPerThread) {
        messagesProcessedPerThread.append(processedMessages);
      }
    } else {
      processedMessages = null;
    }
    return processedMessages;
  }

  public static ObjectBuffer<Message> createMessagesBuffer() {
    ObjectBuffer<Message> processedMessages;

    if (VmSettings.ACTOR_TRACING) {
      processedMessages = new ObjectBuffer<>(MSG_BUFFER_SIZE);

      ObjectBuffer<ObjectBuffer<Message>> messagesPerThread = getMessagesperthread();

      // publish the thread local buffer for later querying
      synchronized (messagesPerThread) {
        messagesPerThread.append(processedMessages);
      }
    } else {
      processedMessages = null;
    }
    return processedMessages;
  }

  public static synchronized void swapBuffer(final ActorProcessingThread t){
    System.out.println("swap");
    returnBuffer(t.getThreadLocalBuffer());

    try {
      t.setThreadLocalBuffer(bufferPool.take());
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public static synchronized ByteBuffer getBuffer() throws InterruptedException{
    return bufferPool.take();
  }

  public static synchronized void returnBuffer(final ByteBuffer b){
    b.limit(b.position());
    b.rewind();
    writerQueue.add(b);
  }

  public static void clearProcessedMessages(){
    for(ObjectBuffer<Mailbox> o : messagesProcessedPerThread){
      o.clear();
    }
  }

  public static void clearCreatedActors(){
    for(ObjectBuffer<SFarReference> o : createdActorsPerThread){
      o.clear();
    }
  }


  //Events
  protected enum Events{
    ActorCreation ((byte) 1, 17),
    PromiseCreation ((byte) 2, 17),
    PromiseResolution ((byte) 3, 17),
    PromiseChained ((byte) 4, 17),
    Mailbox ((byte) 5, 19);
    //for memory events another buffer is needed (the gc callback is on Thread[Service Thread,9,system])

    private final byte id;
    private final int size;

    private Events(final byte id, final int size){
      this.id = id;
      this.size = size;
    }
  };

  public static void actorCreation(final long actorId){
    Thread current = Thread.currentThread();

    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;
      if(t.getThreadLocalBuffer().remaining() < Events.ActorCreation.size) {
        swapBuffer(t);
      }

      ByteBuffer b = t.getThreadLocalBuffer();
      b.put(Events.ActorCreation.id);
      b.putLong(actorId); // id of the created actor
      b.putLong(t.getCurrentMessageId()); // causal message
    }
  }

  public static void promiseCreation(final long promiseId){
    Thread current = Thread.currentThread();

    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;

      if(t.getThreadLocalBuffer().remaining() < Events.PromiseCreation.size) {
        swapBuffer(t);
      }
      ByteBuffer b = t.getThreadLocalBuffer();
      b.put(Events.PromiseCreation.id);
      b.putLong(promiseId); // id of the created promise
      b.putLong(t.getCurrentMessageId()); // causal message
    }
  }

  public static void promiseResolution(final long promiseId){
    Thread current = Thread.currentThread();

    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;

      if(t.getThreadLocalBuffer().remaining() < Events.PromiseResolution.size) {
        swapBuffer(t);
      }
      ByteBuffer b = t.getThreadLocalBuffer();
      b.put(Events.PromiseResolution.id);
      b.putLong(promiseId); // id of the promise
      b.putLong(t.getCurrentMessageId()); // resolving message
    }
  }

  public static void promiseChained(final long parent, final long child){
    Thread current = Thread.currentThread();

    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;

      if(t.getThreadLocalBuffer().remaining() < Events.PromiseChained.size) {
        swapBuffer(t);
      }
      ByteBuffer b = t.getThreadLocalBuffer();
      b.put(Events.PromiseChained.id);
      b.putLong(parent); // id of the parent
      b.putLong(child); // id of the chained promise
    }
  }

  public static void mailboxExecuted(final Mailbox m, final Actor actor){
    Thread current = Thread.currentThread();

    if (current instanceof ActorProcessingThread) {
      ActorProcessingThread t = (ActorProcessingThread) current;

      if(t.getThreadLocalBuffer().remaining() < Events.Mailbox.size + m.size()*8) {
        swapBuffer(t);
      }
      ByteBuffer b = t.getThreadLocalBuffer();
      b.put(Events.Mailbox.id);
      b.putShort((short) m.size()); //number of messages in the mailbox. enough??
      b.putLong(m.getBasemessageId()); //base id for messages
      b.putLong(actor.getActorId()); //receiver of the messages
      for(EventualMessage em : m){
        b.putLong(em.getSender().getActorId()); // sender
        //em.getCausalMessage();
        //em.getSelector();
      }
    }
  }
}
