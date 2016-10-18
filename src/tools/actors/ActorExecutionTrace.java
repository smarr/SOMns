package tools.actors;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import com.sun.management.GarbageCollectionNotificationInfo;

import som.VmSettings;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.vm.ObjectSystem;
import tools.ObjectBuffer;

public class ActorExecutionTrace {

  private static final int MSG_BUFFER_SIZE = 128;

  /** Access to this data structure needs to be synchronized. */
  private static final ObjectBuffer<ObjectBuffer<SFarReference>> createdActorsPerThread =
      VmSettings.ACTOR_TRACING ? new ObjectBuffer<>(VmSettings.NUM_THREADS) : null;

  /** Access to this data structure needs to be synchronized. Typically via {@link createdActorsPerThread} */
  private static final ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesProcessedPerThread =
      VmSettings.ACTOR_TRACING ? new ObjectBuffer<>(VmSettings.NUM_THREADS) : null;

  private static final MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
  private static final List<java.lang.management.GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();

  public static ObjectBuffer<ObjectBuffer<SFarReference>> getAllCreateActors() {
    return createdActorsPerThread;
  }

  public static ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> getAllProcessedMessages() {
    return messagesProcessedPerThread;
  }

  static{setUpGCMonitoring();}

  public static void recordMainActor(final Actor mainActor,
      final ObjectSystem objectSystem) {
    if (VmSettings.ACTOR_TRACING) {
      ObjectBuffer<ObjectBuffer<SFarReference>> actors = getAllCreateActors();
      SFarReference mainActorRef = new SFarReference(mainActor,
          objectSystem.getPlatformClass());

      ObjectBuffer<SFarReference> main = new ObjectBuffer<>(1);
      main.append(mainActorRef);
      actors.append(main);
      //TODO record start time

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

            System.out.println();
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
    System.out.println("Current Memory usage: " + mbean.getHeapMemoryUsage().getUsed() /1024 + " kB");
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

  public static ObjectBuffer<ObjectBuffer<EventualMessage>> createProcessedMessagesBuffer() {
    ObjectBuffer<ObjectBuffer<EventualMessage>> processedMessages;

    if (VmSettings.ACTOR_TRACING) {
      processedMessages = new ObjectBuffer<>(MSG_BUFFER_SIZE);

      ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesProcessedPerThread = getAllProcessedMessages();

      // publish the thread local buffer for later querying
      synchronized (messagesProcessedPerThread) {
        messagesProcessedPerThread.append(processedMessages);
      }
    } else {
      processedMessages = null;
    }
    return processedMessages;
  }

  public static void clearProcessedMessages() {
    for (ObjectBuffer<ObjectBuffer<EventualMessage>> o : messagesProcessedPerThread) {
      o.clear();
    }
  }
}
