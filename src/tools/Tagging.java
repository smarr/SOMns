package tools;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import tools.highlight.Tags;

public abstract class Tagging {
  private Tagging() { }

  public static void collectSourceSectionsAndTags(
      final Iterable<RootNode> rootNodes,
      final Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionsAndTags,
      final Instrumenter instrumenter) {
    for (RootNode root : rootNodes) {
      root.accept(node -> {
        @SuppressWarnings("rawtypes")  Set t = instrumenter.queryTags(node);
        @SuppressWarnings("unchecked") Set<Class<? extends Tags>> tags = t;

        if (tags.size() > 0) {
          if (sourceSectionsAndTags.containsKey(node.getSourceSection())) {
            sourceSectionsAndTags.get(node.getSourceSection()).addAll(tags);
          } else {
            sourceSectionsAndTags.put(node.getSourceSection(), new HashSet<>(tags));
          }
        }
        return true;
      });
    }
  }
}
