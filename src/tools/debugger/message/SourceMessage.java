package tools.debugger.message;

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

  public static class MethodData {
    private final String               name;
    private final SourceCoordinate[]   definition;
    private final SourceCoordinate     sourceSection;

    public MethodData(final String name, final SourceCoordinate[] definition,
        final SourceCoordinate sourceSection) {
      this.name          = name;
      this.definition    = definition;
      this.sourceSection = sourceSection;
    }
  }
}
