package tools.highlight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;


@Registration(id = Highlight.ID)
public final class Highlight extends TruffleInstrument {

  public static final String ID = "highlight";

  private static final Map<SourceSection, Set<Class<? extends Tags>>> nonAstSourceSections = new HashMap<>();
  private static final Set<RootNode> rootNodes = new HashSet<>();

  // TODO: this is a bad hack. but, I don't know how to work around the polyglot engine otherwise
  //       normally, each polyglot engine should have a separate map
  public static void reportNonAstSyntax(final Class<? extends Tags> tag, final SourceSection source) {
    Set<Class<? extends Tags>> set = nonAstSourceSections.computeIfAbsent(source, s -> new HashSet<>(2));
    set.add(tag);
  }

  // TODO: this is a bad hack. but, I don't know how to work around the polyglot engine otherwise
  //       normally, each polyglot engine should have a separate map
  /**
   * @param rootNode, created by the parser.
   *         While we would like to have all, there is not simply way to get them.
   */
  public static void reportParsedRootNode(final RootNode rootNode) {
    rootNodes.add(rootNode);
  }

  public Highlight() { }

  @Override
  protected void onCreate(final Env env) { }

  @Override
  protected void onDispose(final Env env) {
    // TODO: replace this by proper configuration from the environment
    String outputFile = System.getProperty("hl.output", "highlight.json");

    Map<SourceSection, Set<Class<? extends Tags>>> sourceSectionsAndTags = nonAstSourceSections;

    for (RootNode root : rootNodes) {
      root.accept(node -> {
        @SuppressWarnings("rawtypes")  Set t = env.getInstrumenter().queryTags(node);
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
    JsonWriter.fileOut(outputFile, nonAstSourceSections);
  }
}
