package tools.debugger.message;

import tools.debugger.message.Message.OutgoingMessage;


@SuppressWarnings("unused")
public final class ProgramInfoResponse extends OutgoingMessage {
  private final String[] args;

  private ProgramInfoResponse(final String[] args) {
    this.args = args;
  }

  public static ProgramInfoResponse create(final String[] args) {
    return new ProgramInfoResponse(args);
  }
}
