package tools.debugger.message;

import tools.debugger.message.Message.OutgoingMessage;


@SuppressWarnings("unused")
public final class ProgramInfoResponse extends OutgoingMessage {
  private final Object[] args;

  private ProgramInfoResponse(final Object[] args) {
    this.args = args;
  }

  public static ProgramInfoResponse create(final Object[] args) {
    return new ProgramInfoResponse(args);
  }
}
