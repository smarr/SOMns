package tools.debugger.message;

import java.util.ArrayList;
import java.util.Set;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.Method;
import tools.SourceCoordinate;
import tools.SourceCoordinate.TaggedSourceCoordinate;


@SuppressWarnings("unused")
public class SourceMessage extends Message {
  private final SourceData[] sources;

  public SourceMessage(final SourceData[] sources) {
    this.sources  = sources;
  }

  public static class SourceData {
    private final String sourceText;
    private final String mimeType;
    private final String name;
    private final String uri;
    private final TaggedSourceCoordinate[] sections;
    private final MethodData[] methods;

    public SourceData(final String sourceText, final String mimeType,
        final String name, final String uri,
        final TaggedSourceCoordinate[] sections,
        final MethodData[] methods) {
      this.sourceText = sourceText;
      this.mimeType   = mimeType;
      this.name       = name;
      this.uri        = uri;
      this.sections   = sections;
      this.methods    = methods;
    }
  }

  protected static class MethodData {
    private final String               name;
    private final SourceCoordinate[]   definition;
    private final SourceCoordinate     sourceSection;

    protected MethodData(final String name, final SourceCoordinate[] definition,
        final SourceCoordinate sourceSection) {
      this.name          = name;
      this.definition    = definition;
      this.sourceSection = sourceSection;
    }
  }

  public static MethodData[] createMethodDefinitions(final Set<RootNode> rootNodes) {
    ArrayList<MethodData> methods = new ArrayList<>();

    for (RootNode r : rootNodes) {
      assert r instanceof Method;
      Method m = (Method) r;

      if (m.isBlock()) {
        continue;
      }

      SourceSection[] defs = m.getDefinition();
      SourceCoordinate[] definition = new SourceCoordinate[defs.length];
      for (int j = 0; j < defs.length; j += 1) {
        definition[j] = SourceCoordinate.createCoord(defs[j]);
      }

      methods.add(new MethodData(
          m.getName(), definition, SourceCoordinate.create(m.getRootNodeSource())));
    }
    return methods.toArray(new MethodData[0]);
  }
}
