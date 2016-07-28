package tools.debugger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import som.interpreter.Method;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import tools.ObjectBuffer;
import tools.Tagging;
import tools.highlight.Tags;

public final class JsonSerializer {

  private JsonSerializer() { }

  private static int nextSourceId = 0;
  private static int nextSourceSectionId = 0;

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

  public static JSONObjectBuilder createInitialSourceMessage(
      final String type, final Source source,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags,
      final Instrumenter instrumenter, final Set<RootNode> rootNodes) {
    JSONObjectBuilder allSourcesJson = JSONHelper.object();
    String id = sourcesId.get(source);
    assert id != null && !id.equals("");
    allSourcesJson.add(id, ToJson.source(source, id));

    return ToJson.initialSourceMessage(type, allSourcesJson,
        createSourceSections(source, loadedSourcesTags, instrumenter, rootNodes),
        createMethodDefinitions(rootNodes));
  }

  public static JSONObjectBuilder createSourceSections(final Source source,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> sourcesTags,
      final Instrumenter instrumenter, final Set<RootNode> rootNodes) {
    Set<SourceSection> sections = new HashSet<>();

    Map<SourceSection, Set<Class<? extends Tags>>> tagsForSections = sourcesTags.get(source);
    Tagging.collectSourceSectionsAndTags(rootNodes, tagsForSections, instrumenter);

    if (tagsForSections != null) {
      for (SourceSection section : tagsForSections.keySet()) {
        if (section.getSource() == source) {
          sections.add(section);
          createSourceSectionId(section);
        }
      }
    }

    return ToJson.sourceSections(
        sections, sourcesId, sourceSectionId, tagsForSections);
  }

  public static JSONArrayBuilder createMethodDefinitions(final Set<RootNode> rootNodes) {
    JSONArrayBuilder arr = JSONHelper.array();

    for (RootNode r : rootNodes) {
      assert r instanceof Method;
      Method m = (Method) r;
      String ssId = createSourceSectionId(m.getSourceSection());
      String sId  = createSourceId(m.getSourceSection().getSource());
      arr.add(ToJson.method(m, ssId, sId));
    }
    return arr;
  }

  public static JSONObjectBuilder createFrame(final Node node,
      final FrameInstance stackFrame,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> tags) {
    JSONObjectBuilder frame = JSONHelper.object();
    if (node != null) {
      SourceSection section = node.getEncapsulatingSourceSection();
      if (section != null) {
        Map<SourceSection, Set<Class<? extends Tags>>> tagsForSections = tags.get(section.getSource());
        frame.add("sourceSection", ToJson.sourceSection(
            section, createSourceSectionId(section),
            sourcesId.get(section.getSource()),
            (tagsForSections == null) ? null : tagsForSections.get(section)));
      }
    }

    RootCallTarget rct = (RootCallTarget) stackFrame.getCallTarget();
    String methodName = rct.getRootNode().getName();

    frame.add("methodName", methodName);

    // TODO: stack frame content, or on demand?
    // stackFrame.getFrame(FrameAccess.READ_ONLY, true);
    return frame;
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

  public static JSONObjectBuilder createSuspendedEventJson(
      final SuspendedEvent e, final Node suspendedNode, final RootNode suspendedRoot,
      final Source suspendedSource, final String id,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> tags,
      final Instrumenter instrumenter, final Map<Source, Set<RootNode>> roots) {
    JSONObjectBuilder builder  = JSONHelper.object();
    builder.add("type", "suspendEvent");

    // first add the source info, because this builds up also tag info
    builder.add("sourceId", createSourceId(suspendedSource));
    builder.add("sections", createSourceSections(suspendedSource, tags,
        instrumenter, roots.get(suspendedSource)));

    JSONArrayBuilder stackJson = JSONHelper.array();
    List<FrameInstance> stack = e.getStack();

    for (int stackIndex = 0; stackIndex < stack.size(); stackIndex++) {
      final Node callNode = stackIndex == 0 ? suspendedNode : stack.get(stackIndex).getCallNode();
      stackJson.add(createFrame(callNode, stack.get(stackIndex), tags));
    }
    builder.add("stack", stackJson);
    builder.add("topFrame", ToJson.topFrameJson(e.getFrame(), suspendedRoot));
    builder.add("id", id);
    return builder;
  }
}
