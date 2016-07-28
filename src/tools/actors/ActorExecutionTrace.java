package tools.actors;

import com.oracle.truffle.api.debug.Breakpoint;

import som.VmSettings;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.vm.ObjectSystem;
import tools.ObjectBuffer;
import tools.debugger.FrontendConnector.BreakpointLocation;

public class ActorExecutionTrace {

  /** Access to this data structure needs to be synchronized. */
  private static final ObjectBuffer<ObjectBuffer<SFarReference>> createdActorsPerThread =
      VmSettings.ACTOR_TRACING ? new ObjectBuffer<>(VmSettings.NUM_THREADS) : null;

  /** Access to this data structure needs to be synchronized. Typically via {@link createdActorsPerThread} */
  private static final ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesProcessedPerThread =
      VmSettings.ACTOR_TRACING ? new ObjectBuffer<>(VmSettings.NUM_THREADS) : null;

  public static ObjectBuffer<ObjectBuffer<SFarReference>> getAllCreateActors() {
    return createdActorsPerThread;
  }

  public static ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> getAllProcessedMessages() {
    return messagesProcessedPerThread;
  }

  public static void recordMainActor(final Actor mainActor,
      final ObjectSystem objectSystem) {
    if (VmSettings.ACTOR_TRACING) {
      ObjectBuffer<ObjectBuffer<SFarReference>> actors = getAllCreateActors();
      SFarReference mainActorRef = new SFarReference(mainActor,
          objectSystem.getPlatformClass());

      ObjectBuffer<SFarReference> main = new ObjectBuffer<>(1);
      main.append(mainActorRef);
      actors.append(main);
    }
  }

  public static ObjectBuffer<SFarReference> createActorBuffer() {
    ObjectBuffer<SFarReference> createdActors;

    if (VmSettings.ACTOR_TRACING) {
      createdActors = new ObjectBuffer<>(128);

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
      processedMessages = new ObjectBuffer<>(128);

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

  /**
   * Assign breakpoint for the actor that is the receiver of the message.
   */
  public static void assignBreakpoint(final Breakpoint bp, final Actor actor,
      final BreakpointLocation bl, final boolean receiver) {

    ObjectBuffer<ObjectBuffer<SFarReference>> actorsPerThread = getAllCreateActors();
    for (ObjectBuffer<SFarReference> perThread : actorsPerThread) {
      for (SFarReference a : perThread) {
        if (a.getActor().equals(actor)) {
          a.getActor().getLocalManager().addBreakpoint(bp, bl, receiver);
          break;
        }
      }
    }
  }
}
