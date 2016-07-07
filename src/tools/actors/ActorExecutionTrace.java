package tools.actors;

import java.util.HashMap;
import java.util.Map;

import som.VmSettings;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import tools.ObjectBuffer;

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

      public static Map<SFarReference, String> createActorMap(
          final ObjectBuffer<ObjectBuffer<SFarReference>> actorsPerThread) {
        HashMap<SFarReference, String> map = new HashMap<>();
        int numActors = 0;

        for (ObjectBuffer<SFarReference> perThread : actorsPerThread) {
          for (SFarReference a : perThread) {
            assert !map.containsKey(a);
            map.put(a, "a-" + numActors);
            numActors += 1;
          }
        }
        return map;
      }
}
