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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;


/**
 * <p>
 * The Highlight tool generates a JSON file with the source code that was
 * executed by the polyglot engine and uses {@link Tags} to provide information
 * for syntax highlighting based on source sections.
 *
 * <p>
 * Highlight expects that the root nodes that should be included are reported
 * to it using the {@link reportParsedRootNode} function.
 * For elements that are not part of the AST, the {@link reportNonAstSyntax}
 * function can be used.
 *
 * <p>
 * The output file can be configured by the Java property: hl.output
 */
@Registration(id = Highlight.ID)
public final class Highlight extends TruffleInstrument {

  public static final String ID = "highlight";

  private static final Map<SourceSection, Set<Class<? extends Tags>>> nonAstSourceSections = new HashMap<>();
  private static final Set<RootNode> rootNodes = new HashSet<>();

  /**
   * Report an element for highlighting that is not part of an AST,
   * and thus is not known to this tool from a root node
   * ({@see reportParsedRootNode}).
   *
   * @param tag, indicating the type of the element
   * @param source, the source section describing the location of the element
   */
  // TODO: this is a bad hack. but, I don't know how to work around the polyglot engine otherwise
  //       normally, each polyglot engine should have a separate map
  public static void reportNonAstSyntax(final Class<? extends Tags> tag, final SourceSection source) {
    Set<Class<? extends Tags>> set = nonAstSourceSections.computeIfAbsent(source, s -> new HashSet<>(2));
    set.add(tag);
  }

  /**
   * Report a root node of which the AST nodes should be used for highlighting
   * code.
   *
   * @param rootNode, created by the parser.
   *         While we would like to have all, there is not simply way to get them.
   */
  // TODO: this is a bad hack. but, I don't know how to work around the polyglot engine otherwise
  //       normally, each polyglot engine should have a separate map
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
