package tools.debugger.message;

import tools.SourceCoordinate.TaggedSourceCoordinate;
import tools.debugger.message.Message.OutgoingMessage;

/**
 * This message is send to update the front-end with the latest source section
 * information.
 *
 * <p>This is useful to have dynamic semantic highlighting for a dynamic language.
 * The highlighting can then also indicate field reads done by slot getters,
 * distinguish local accesses from message sends in a uniform language, etc.
 */
@SuppressWarnings("unused")
public class UpdateSourceSections extends OutgoingMessage {
  private final SourceInfo[] updates;

  public UpdateSourceSections(final SourceInfo[] updates) {
    this.updates = updates;
  }

  public static class SourceInfo {
    private final String sourceUri;
    private final TaggedSourceCoordinate[] sections;

    public SourceInfo(final String sourceUri,
        final TaggedSourceCoordinate[] sections) {
      this.sourceUri = sourceUri;
      this.sections  = sections;
    }
  }
}
