package tools.debugger;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import som.vmobjects.SClass;
import tools.highlight.Tags;


/**
 * Creates JSON representations for objects based on simple data.
 *
 * Note, any kind of data collection should be done for instance in
 * {@link JsonSerializer}.
 */
public final class ToJson {

  public static JSONObjectBuilder sourceAndSectionMessage(final String type,
      final JSONObjectBuilder sources, final JSONObjectBuilder sections) {
    JSONObjectBuilder root = JSONHelper.object();
    root.add("type",     type);
    root.add("sources",  sources);
    root.add("sections", sections);
    return root;
  }

  public static JSONObjectBuilder source(final Source s, final String id) {
    JSONObjectBuilder builder = JSONHelper.object();
    builder.add("id", id);
    builder.add("sourceText", s.getCode());
    builder.add("mimeType",   s.getMimeType());
    builder.add("name",       s.getName());
    builder.add("uri",        s.getURI().toString());
    return builder;
  }

  public static JSONObjectBuilder sourceSection(final SourceSection ss,
      final String id, final String sourceId,
      final Set<Class<? extends Tags>> tags) {
    JSONObjectBuilder builder = JSONHelper.object();

    builder.add("id", id);
    builder.add("firstIndex", ss.getCharIndex());
    builder.add("length",     ss.getCharLength());
    builder.add("line",       ss.getStartLine());
    builder.add("column",     ss.getStartColumn());
    builder.add("description", ss.getShortDescription());
    builder.add("sourceId", sourceId);

    if (tags != null && tags.size() > 0) {
      JSONArrayBuilder arr = JSONHelper.array();
      for (Class<? extends Tags> tagClass : tags) {
        arr.add(tagClass.getSimpleName());
      }
      builder.add("tags", arr);
    }
    return builder;
  }

  public static JSONObjectBuilder sourceSections(
      final Set<SourceSection> allSections,
      final Map<Source, String> sourcesId,
      final Map<SourceSection, String> sourceSectionId,
      final Map<SourceSection, Set<Class<? extends Tags>>> tags) {
    JSONObjectBuilder allSectionsJson = JSONHelper.object();
    for (SourceSection ss : allSections) {
      allSectionsJson.add(sourceSectionId.get(ss),
          ToJson.sourceSection(ss, sourceSectionId.get(ss),
              sourcesId.get(ss.getSource()), tags.get(ss)));
    }
    return allSectionsJson;
  }

  public static JSONObjectBuilder topFrameJson(final MaterializedFrame frame,
      final RootNode root) {
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
    frameJson.add("slots",     slots);
    return frameJson;
  }

  public static JSONObjectBuilder farReference(final String id, final SFarReference a) {
    Object value = a.getValue();
    assert value instanceof SClass;
    SClass actorClass = (SClass) value;

    JSONObjectBuilder builder = JSONHelper.object();
    builder.add("id", id);
    builder.add("name",     actorClass.getName().getString());
    builder.add("typeName", actorClass.getName().getString());

    return builder;
  }

  public static JSONObjectBuilder message(final String senderId,
      final String targetId, final int mId, final EventualMessage m) {
    JSONObjectBuilder jsonM = JSONHelper.object();
    jsonM.add("id", "m-" + mId);
    jsonM.add("sender",   senderId);
    jsonM.add("receiver", targetId);
    return jsonM;
  }
}
