package dym;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import som.compiler.SourcecodeCompiler;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;


public final class JsonWriter {

  public static final String METHOD_INVOCATION_PROFILE = "methodInvocationProfile";
  public static final String METHOD_CALLSITE           = "methodCallsite";
  public static final String NEW_OBJECT_COUNT          = "newObjectCount";
  public static final String NEW_ARRAY_COUNT           = "newArrayCount";
  public static final String FIELD_READS      = "fieldReads";
  public static final String FIELD_WRITES     = "fieldWrites";
  public static final String CLASS_READS      = "classReads";
  public static final String BRANCH_PROFILES  = "branchProfile";
  public static final String LITERAL_READS    = "literalReads";
  public static final String LOCAL_READS      = "localReads";
  public static final String LOCAL_WRITES     = "localWrites";
  public static final String OPERATIONS       = "operations";
  public static final String LOOPS            = "loops";

  private final Map<String, Map<SourceSection, ? extends JsonSerializable>> data;
  private final String outputFile;

  private JsonWriter(final Map<String, Map<SourceSection, ? extends JsonSerializable>> data,
      final String outputFile) {
    this.data       = data;
    this.outputFile = outputFile;
  }

  private Set<SourceSection> allSections() {
    Set<SourceSection> allSections = new HashSet<>();
    data.values().forEach(map -> allSections.addAll(map.keySet()));
    return allSections;
  }

  private <U> Map<U, String> createIdMap(final Set<U> set, final String idPrefix) {
    Map<U, String> eToId = new HashMap<>();

    int i = 0;
    for (U e : set) {
      eToId.put(e, idPrefix + i);
      i += 1;
    }
    return eToId;
  }

  private JSONObjectBuilder sourceToJson(final Source s, final String id) {
    JSONObjectBuilder builder = JSONHelper.object();
    builder.add("id", id);
    builder.add("sourceText", s.getCode());
    builder.add("mimeType", s.getMimeType());
    builder.add("name", s.getName());
    builder.add("shortName", s.getShortName());
    return builder;
  }

  private JSONObjectBuilder sectionToJson(final SourceSection ss, final String id, final Map<Source, String> sourceToId) {
    JSONObjectBuilder builder = JSONHelper.object();

    builder.add("id", id);
    builder.add("firstIndex", ss.getCharIndex());
    builder.add("length", ss.getCharLength());
    builder.add("identifier", ss.getIdentifier());
    builder.add("description", ss.getShortDescription());
    builder.add("sourceId", sourceToId.get(ss.getSource()));

    if (ss.getTags() != null && ss.getTags().length > 0) {
      JSONArrayBuilder arr = JSONHelper.array();
      for (String tag : ss.getTags()) {
        arr.add(tag);
      }
      builder.add("tags", arr);
    }
    builder.add("data", collectDataForSection(ss));

    return builder;
  }

  private JSONObjectBuilder collectDataForSection(final SourceSection section) {
    JSONObjectBuilder result = JSONHelper.object();
    data.forEach((name, map) -> {
      JsonSerializable profile = map.get(section);
      if (profile != null) {
        result.add(name, profile.toJson());
      }
    });
    return result;
  }

  public static void fileOut(final Map<String, Map<SourceSection, ? extends JsonSerializable>> data,
      final String outputFile) {
    new JsonWriter(data, outputFile).createJsonFile();
  }

  public void createJsonFile() {
    Set<SourceSection> allSections = allSections();

    Set<Source> allSources = new HashSet<>();
    allSections.forEach(ss -> allSources.add(ss.getSource()));

    for (Source s : allSources) {
      Set<SourceSection> annotations = SourcecodeCompiler.getSyntaxAnnotations(s);
      if (annotations != null) {
        allSections.addAll(annotations);
      }
    }

    Map<Source, String> sourceToId = createIdMap(allSources, "s-");
    Map<SourceSection, String> sectionToId = createIdMap(allSections, "ss-");

    JSONObjectBuilder allSourcesJson = JSONHelper.object();
    for (Source s : allSources) {
      String id = sourceToId.get(s);
      assert id != null && !id.equals("");
      allSourcesJson.add(id, sourceToJson(s, id));
    }

    JSONObjectBuilder allSectionsJson = JSONHelper.object();
    for (SourceSection ss : allSections) {
      allSectionsJson.add(sectionToId.get(ss), sectionToJson(ss, sectionToId.get(ss), sourceToId));
    }

    JSONObjectBuilder root = JSONHelper.object();
    root.add("sources", allSourcesJson);
    root.add("sections", allSectionsJson);

    try {
      try (PrintWriter jsonFile = new PrintWriter(new File(outputFile))) {
        jsonFile.println(root.toString());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
