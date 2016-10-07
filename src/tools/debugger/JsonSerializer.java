package tools.debugger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import tools.ObjectBuffer;

public final class JsonSerializer {

  private JsonSerializer() { }

  public static JSONObjectBuilder createMessageHistoryJson(
      final ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesPerThread,
      final Map<SFarReference, String> actorsToIds,
      final Map<Actor, String> actorObjsToIds) {
    JSONArrayBuilder actors = JSONHelper.array();
    for (Entry<SFarReference, String> e : actorsToIds.entrySet()) {
      actors.add(ToJson.farReference(e.getValue(), e.getKey()));
    }

    JSONObjectBuilder messages = JSONHelper.object();
    Map<Actor, Set<JSONObjectBuilder>> perReceiver = messagesPerReceiver(
        messagesPerThread, actorObjsToIds);

    for (Entry<Actor, Set<JSONObjectBuilder>> e : perReceiver.entrySet()) {
      JSONArrayBuilder arr = JSONHelper.array();
      for (JSONObjectBuilder m : e.getValue()) {
        arr.add(m);
      }
      messages.add(actorObjsToIds.get(e.getKey()), arr);
    }

    JSONObjectBuilder history = JSONHelper.object();
    history.add("messages", messages);
    history.add("actors", actors);

    JSONObjectBuilder msg = JSONHelper.object();
    msg.add("type", "messageHistory");
    msg.add("messageHistory", history);
    return msg;
  }

  private static Map<Actor, Set<JSONObjectBuilder>> messagesPerReceiver(
      final ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesPerThread,
      final Map<Actor, String> actorObjsToIds) {
    Map<Actor, Set<JSONObjectBuilder>> perReceiver = new HashMap<>();
    int mId = 0;
    for (ObjectBuffer<ObjectBuffer<EventualMessage>> perThread : messagesPerThread) {
      for (ObjectBuffer<EventualMessage> perBatch : perThread) {
        for (EventualMessage m : perBatch) {
          perReceiver.computeIfAbsent(m.getTarget(), a -> new HashSet<>());

          assert actorObjsToIds.containsKey(m.getSender());
          assert actorObjsToIds.containsKey(m.getTarget());

          JSONObjectBuilder jsonM = ToJson.message(
              actorObjsToIds.get(m.getSender()),
              actorObjsToIds.get(m.getTarget()), mId, m);
          perReceiver.get(m.getTarget()).add(jsonM);
          mId += 1;
        }
      }
    }
    return perReceiver;
  }
}
