package bd.source;

import java.net.URI;
import java.util.Objects;


/**
 * Is a {@link SourceCoordinate} with an extra URI to identify it globally.
 * Otherwise, behaves the same.
 */
public class FullSourceCoordinate extends SourceCoordinate {
  public final URI uri;

  protected FullSourceCoordinate(final URI uri, final int startLine,
      final int startColumn, final int charIndex, final int length) {
    super(startLine, startColumn, charIndex, length);
    this.uri = uri;
  }

  protected FullSourceCoordinate() {
    this(null, 0, 0, 0, 0);
  }

  @Override
  public int hashCode() {
    // ignore charIndex, not always set
    return Objects.hash(uri, startLine, startColumn, /* charIndex, */ charLength);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
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

  @Override
  public String toString() {
    return "SrcCoord(line: " + startLine + ", col: " + startColumn + ", charlength:"
        + charLength + ", uri: " + uri.getPath() + ")";
  }
}
