package tools.debugger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import tools.ObjectBuffer;

public final class JsonSerializer {

  private JsonSerializer() { }

  private static int nextSourceId = 0;
  private static int nextSourceSectionId = 0;

  // TODO: remove these maps, should not be necessary any longer
  private static final Map<Source, String> sourcesId = new HashMap<>();
  private static final Map<String, Source> idSources = new HashMap<>();
  private static final Map<SourceSection, String> sourceSectionId = new HashMap<>();
  private static final Map<String, SourceSection> idSourceSections = new HashMap<>();

  public static String createSourceId(final Source source) {
    return sourcesId.computeIfAbsent(source, src -> {
      int n = nextSourceId;
      nextSourceId += 1;
      String id = "s-" + n;
      idSources.put(id, src);
      return id;
    });
  }

  public static String createSourceSectionId(final SourceSection source) {
    return sourceSectionId.computeIfAbsent(source, s -> {
      int n = nextSourceSectionId;
      nextSourceSectionId += 1;
      String id = "ss-" + n;
      idSourceSections.put(id, source);
      return id;
    });
  }

  public static Source getSource(final String id) {
    return idSources.get(id);
  }

  public static SourceSection getSourceSection(final String id) {
    return idSourceSections.get(id);
  }

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
