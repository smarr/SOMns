/**
 * Copyright (c) 2016 Stefan Marr
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tools.highlight;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;


public final class JsonWriter {

  public static void fileOut(final String outputFile,
      final Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionTags) {
    new JsonWriter(outputFile, sourceSectionTags).createJsonFile();
  }

  public static String createJson(final String type,
      final Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionTags) {
    return new JsonWriter(sourceSectionTags).createJsonString(type);
  }

  public static String createJson(final String type,
      final Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionTags,
      final Map<Source, String> sourceToId,
      final Map<SourceSection, String> sectionToId) {
    return new JsonWriter(sourceSectionTags).createJsonString(
        type, sourceToId, sectionToId);
  }

  private final String outputFile;
  private final Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionTags;

  private JsonWriter(
      final Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionTags) {
    this(null, sourceSectionTags);
  }

  private JsonWriter(final String outputFile,
      final Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionTags) {
    this.sourceSectionTags = sourceSectionTags;
    this.outputFile = outputFile;
  }

  public void createJsonFile() {
    try {
      try (PrintWriter jsonFile = new PrintWriter(new File(outputFile))) {
        jsonFile.println(createJsonString(null));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public String createJsonString(final String type) {
    return createJsonString(type, null, null);
  }
  public String createJsonString(final String type,
      Map<Source, String> sourceToId, Map<SourceSection, String> sectionToId) {
    Set<SourceSection> allSections = sourceSectionTags.keySet();

    Set<Source> allSources = new HashSet<>();
    allSections.forEach(ss -> allSources.add(ss.getSource()));

    // TODO:
//    for (Source s : allSources) {
//      Set<SourceSection> annotations = SourcecodeCompiler.getSyntaxAnnotations(s);
//      if (annotations != null) {
//        allSections.addAll(annotations);
//      }
//    }

    if (sourceToId == null) {
      sourceToId  = createIdMap(allSources, "s-");
    }
    if (sectionToId == null) {
      sectionToId = createIdMap(allSections, "ss-");
    }

    JSONObjectBuilder allSourcesJson = JSONHelper.object();
    for (Source s : allSources) {
      String id = sourceToId.get(s);
      assert id != null && !id.equals("");
      allSourcesJson.add(id, sourceToJson(s, id));
    }

    JSONObjectBuilder allSectionsJson = createJsonForSourceSections(sourceToId,
        sectionToId, allSections, sourceSectionTags);

    JSONObjectBuilder root = JSONHelper.object();
    if (type != null) {
      root.add("type", type);
    }

    root.add("sources", allSourcesJson);
    root.add("sections", allSectionsJson);

    return root.toString();
  }

  public static JSONObjectBuilder createJsonForSourceSections(
      final Map<Source, String> sourceToId,
      final Map<SourceSection, String> sectionToId,
      final Set<SourceSection> allSections,
      final Map<SourceSection, Set<Class<? extends Tags>>> tags) {
    JSONObjectBuilder allSectionsJson = JSONHelper.object();
    for (SourceSection ss : allSections) {
      allSectionsJson.add(sectionToId.get(ss), sectionToJson(ss, sectionToId.get(ss), sourceToId, tags));
    }
    return allSectionsJson;
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

  private static JSONObjectBuilder sectionToJson(final SourceSection ss,
      final String id, final Map<Source, String> sourceToId,
      final Map<SourceSection, Set<Class<? extends Tags>>> tags) {
    return sectionToJson(ss, id, sourceToId, tags.get(ss));
  }

  public static JSONObjectBuilder sectionToJson(final SourceSection ss,
      final String id, final Map<Source, String> sourceToId,
      final Set<Class<? extends Tags>> tags) {
    JSONObjectBuilder builder = JSONHelper.object();

    builder.add("id", id);
    builder.add("firstIndex", ss.getCharIndex());
    builder.add("length", ss.getCharLength());
    builder.add("identifier", ss.getIdentifier());
    builder.add("line", ss.getStartLine());
    builder.add("column", ss.getStartColumn());
    builder.add("description", ss.getShortDescription());
    builder.add("sourceId", sourceToId.get(ss.getSource()));

    if (tags != null && tags.size() > 0) {
      JSONArrayBuilder arr = JSONHelper.array();
      for (Class<? extends Tags> tagClass : tags) {
        arr.add(tagClass.getSimpleName());
      }
      builder.add("tags", arr);
    }
//    builder.add("data", collectDataForSection(ss));
    return builder;
  }
}
