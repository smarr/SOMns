package tools.debugger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.vmobjects.SClass;
import tools.ObjectBuffer;
import tools.Tagging;
import tools.highlight.JsonWriter;
import tools.highlight.Tags;

public final class JsonSerializer {

  private JsonSerializer() { }

  public static JSONObjectBuilder toJson(final String id, final SFarReference a) {
    Object value = a.getValue();
    assert value instanceof SClass;
    SClass actorClass = (SClass) value;

    JSONObjectBuilder builder = JSONHelper.object();
    builder.add("id", id);
    builder.add("name",     actorClass.getName().getString());
    builder.add("typeName", actorClass.getName().getString());

    return builder;
  }

  private static int nextSourceId = 0;
  private static int nextSourceSectionId = 0;

  private static final Map<Source, String> sourcesId = new HashMap<>();
  private static final Map<String, Source> idSources = new HashMap<>();
  private static final Map<SourceSection, String> sourceSectionId = new HashMap<>();


  public static String createSourceId(final Source source) {
    return sourcesId.computeIfAbsent(source, src -> {
      int n = nextSourceId;
      nextSourceId += 1;
      String id = "s-" + n;
      idSources.put(id, src);
      return id;
    });
  }

  public static String getExistingSourceId(final Source source) {
    String id = sourcesId.get(source);
    assert id != null;
    return id;
  }

  public static String createSourceSectionId(final SourceSection source) {
    return sourceSectionId.computeIfAbsent(source, s -> {
      int n = nextSourceSectionId;
      nextSourceSectionId += 1;
      return "ss-" + n;
    });
  }

  public static Source getSource(final String id) {
    return idSources.get(id);
  }

  public static String createSourceAndSectionMessage(final Source source,
      final Map<SourceSection, Set<Class<? extends Tags>>> tags) {
    return JsonWriter.createJson("source", tags, sourcesId, sourceSectionId);
  }

  public static JSONObjectBuilder createJsonForSourceSections(final Source source, final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> sourcesTags, final Instrumenter instrumenter, final Map<Source, Set<RootNode>> rootNodes) {
    Set<SourceSection> sections = new HashSet<>();

    Map<SourceSection, Set<Class<? extends Tags>>> tagsForSections = sourcesTags.get(source);
    Tagging.collectSourceSectionsAndTags(rootNodes.get(source), tagsForSections, instrumenter);

    for (SourceSection section : tagsForSections.keySet()) {
      if (section.getSource() == source) {
        sections.add(section);
        createSourceSectionId(section);
      }
    }

    return JsonWriter.createJsonForSourceSections(sourcesId, sourceSectionId, sections, tagsForSections);
  }

  public static JSONObjectBuilder createFrame(final Node node,
      final FrameInstance stackFrame,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> tags) {
    JSONObjectBuilder frame = JSONHelper.object();
    if (node != null) {
      SourceSection section = node.getEncapsulatingSourceSection();
      if (section != null) {
        Map<SourceSection, Set<Class<? extends Tags>>> tagsForSections = tags.get(section.getSource());
        frame.add("sourceSection", JsonWriter.sectionToJson(
            section, createSourceSectionId(section), sourcesId,
            (tagsForSections == null) ? null : tagsForSections.get(section)));
      }
    }

    RootCallTarget rct = (RootCallTarget) stackFrame.getCallTarget();
    SourceSection rootSource = rct.getRootNode().getSourceSection();
    String methodName;
    if (rootSource != null) {
      methodName = rootSource.getIdentifier();
    } else {
      methodName = rct.toString();
    }
    frame.add("methodName", methodName);

    // TODO: stack frame content, or on demand?
    // stackFrame.getFrame(FrameAccess.READ_ONLY, true);
    return frame;
  }

  public static JSONObjectBuilder createTopFrameJson(final MaterializedFrame frame, final RootNode root) {
    JSONArrayBuilder arguments = JSONHelper.array();
    for (Object o : frame.getArguments()) {
      arguments.add(o.toString());
    }

    JSONObjectBuilder slots = JSONHelper.object();
    for (FrameSlot slot : root.getFrameDescriptor().getSlots()) {
      Object value = frame.getValue(slot);
      slots.add(slot.getIdentifier().toString(),
          Objects.toString(value));
    }

    JSONObjectBuilder frameJson = JSONHelper.object();
    frameJson.add("arguments", arguments);
    frameJson.add("slots", slots);
    return frameJson;
  }

  public static JSONObjectBuilder createMessageHistoryJson(
      final ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesPerThread,
      final Map<SFarReference, String> actorsToIds,
      final Map<Actor, String> actorObjsToIds) {
    JSONArrayBuilder actors = JSONHelper.array();
    for (Entry<SFarReference, String> e : actorsToIds.entrySet()) {
      actors.add(toJson(e.getValue(), e.getKey()));
    }

    int mId = 0;
    JSONObjectBuilder messages = JSONHelper.object();

    Map<Actor, Set<JSONObjectBuilder>> perReceiver = new HashMap<>();
    for (ObjectBuffer<ObjectBuffer<EventualMessage>> perThread : messagesPerThread) {
      for (ObjectBuffer<EventualMessage> perBatch : perThread) {
        for (EventualMessage m : perBatch) {
          perReceiver.computeIfAbsent(m.getTarget(), a -> new HashSet<>());

          JSONObjectBuilder jsonM = JSONHelper.object();
          jsonM.add("id", "m-" + mId);
          mId += 1;
          assert actorObjsToIds.containsKey(m.getSender());
          assert actorObjsToIds.containsKey(m.getTarget());
          jsonM.add("sender", actorObjsToIds.get(m.getSender()));
          jsonM.add("receiver", actorObjsToIds.get(m.getTarget()));
          perReceiver.get(m.getTarget()).add(jsonM);
        }
      }
    }

    for (Entry<Actor, Set<JSONObjectBuilder>> e : perReceiver.entrySet()) {
      JSONArrayBuilder arr = JSONHelper.array();
      for (JSONObjectBuilder m : e.getValue()) {
        arr.add(m);
      }
      messages.add(actorObjsToIds.get(e.getKey()), arr);
    }

    JSONObjectBuilder history = JSONHelper.object();
    history.add("messages", messages); // TODO
    history.add("actors", actors);

    JSONObjectBuilder msg = JSONHelper.object();
    msg.add("type", "messageHistory");
    msg.add("messageHistory", history);
    return msg;
  }
}
