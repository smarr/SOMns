package tools;


/**
 * Represents a potentially empty range of source characters, for a potentially
 * not yet loaded source.
 */
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
}
