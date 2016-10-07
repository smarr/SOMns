package tools;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.source.SourceSection;

import tools.debugger.Tags;

/**
 * Represents a potentially empty range of source characters, for a potentially
 * not yet loaded source.
 */
// TODO: this needs further cleanup by making class names nicer
// and, some instance might not have a charIndex
public class SourceCoordinate {
  public final int startLine;
  public final int startColumn;
  public final transient int charIndex;
  public final int charLength;

  public SourceCoordinate(final int startLine, final int startColumn,
      final int charIndex, final int length) {
    this.startLine   = startLine;
    this.startColumn = startColumn;
    this.charIndex   = charIndex;
    this.charLength  = length;
    assert startLine   >= 0;
    assert startColumn >= 0;
    assert charIndex   >= 0 || charIndex == -1;
  }

  @Override
  public String toString() {
    return "SrcCoord(line: " + startLine + ", col: " + startColumn + ")";
  }

  public static class FullSourceCoordinate extends SourceCoordinate {
    public final URI uri;

    protected FullSourceCoordinate(final URI uri, final int startLine,
        final int startColumn, final int charIndex, final int length) {
      super(startLine, startColumn, charIndex, length);
      this.uri = uri;
    }

    @Override
    public int hashCode() {
      // ignore charIndex, not always set
      return Objects.hash(uri, startLine, startColumn, /* charIndex, */ charLength);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) { return true; }
      if (!(o instanceof FullSourceCoordinate)) {
        return false;
      }

      FullSourceCoordinate sc = (FullSourceCoordinate) o;
      // ignore charIndex, not always set
      return sc.uri.equals(uri) &&
          sc.startLine == startLine && sc.startColumn == startColumn &&
          // sc.charIndex == charIndex &&
          sc.charLength == charLength;
    }
  }

  public static class TaggedSourceCoordinate extends SourceCoordinate {
    private final String[] tags;

    protected TaggedSourceCoordinate(final int startLine, final int startColumn,
      final int charIndex, final int length, final String[] tags) {
      super(startLine, startColumn, charIndex, length);
      this.tags = tags;
    }
  }

  public static SourceCoordinate createCoord(final SourceSection section) {
    return new SourceCoordinate(section.getStartLine(), section.getStartColumn(),
        section.getCharIndex(), section.getCharLength());
  }

  public static TaggedSourceCoordinate create(final SourceSection section,
      final Set<Class<? extends Tags>> tags) {
    String[] strTags = new String[tags.size()];

    int i = 0;
    for (Class<? extends Tags> tagClass : tags) {
      strTags[i] = tagClass.getSimpleName();
      i += 1;
    }

    return new TaggedSourceCoordinate(section.getStartLine(),
        section.getStartColumn(),
        section.getCharIndex(), section.getCharLength(), strTags);
  }

  public static FullSourceCoordinate create(final SourceSection section) {
    return new FullSourceCoordinate(section.getSource().getURI(),
        section.getStartLine(), section.getStartColumn(),
        section.getCharIndex(), section.getCharLength());
  }

  public static FullSourceCoordinate create(final URI sourceUri, final int startLine,
      final int startColumn, final int charLength) {
    return new FullSourceCoordinate(sourceUri, startLine, startColumn, -1, charLength);
  }

  public static FullSourceCoordinate create(final String sourceUri, final int startLine,
      final int startColumn, final int charLength) {
    try {
      return create(new URI(sourceUri), startLine, startColumn, charLength);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
