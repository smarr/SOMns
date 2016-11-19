package tools.debugger.message;

import tools.debugger.WebDebugger.Suspension;

public final class StoppedMessage extends Message {
  private String  reason;
  private int     threadId;
  private String  text;
  private boolean allThreadsStopped;

  private StoppedMessage(final Reason reason, final int threadId,
      final String text) {
    this.reason   = reason.value;
    this.threadId = threadId;
    this.text     = text;
    this.allThreadsStopped = false;
  }

  private enum Reason {
    step("step"),
    breakpoint("breakpoint"),
    exception("exception"),
    pause("pause");

    private final String value;

    Reason(final String value) {
      this.value = value;
    }
  }

  public static StoppedMessage create(final Suspension suspension) {
    assert !suspension.getEvent().getBreakpoints().isEmpty() : "Need to support other reasons for suspension";
    Reason reason = Reason.breakpoint;
    return new StoppedMessage(reason, suspension.activityId, ""); // TODO: look into additional details that can be provided as text
  }
}
