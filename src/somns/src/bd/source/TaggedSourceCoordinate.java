package bd.source;

/**
 * Is a {@link SourceCoordinate} with additional tags to characterize the source range.
 * Otherwise, behaves the same.
 */
public class TaggedSourceCoordinate extends SourceCoordinate {
  public final String[] tags;

  protected TaggedSourceCoordinate(final int startLine, final int startColumn,
      final int charIndex, final int length, final String[] tags) {
    super(startLine, startColumn, charIndex, length);
    this.tags = tags;
  }
}
