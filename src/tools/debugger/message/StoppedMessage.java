package tools.debugger.message;

import som.interpreter.actors.Actor;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.OutgoingMessage;


@SuppressWarnings("unused")
public final class StoppedMessage extends OutgoingMessage {
  private String  reason;
  private int     activityId;
  private String  activityType;
  private String  text;
  private boolean allThreadsStopped;

  private StoppedMessage(final Reason reason, final int activityId,
      final ActivityType type, final String text) {
    this.reason     = reason.value;
    this.activityId = activityId;
    this.activityType = type.value;
    this.text       = text;
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

  private enum ActivityType {
    Actor("Actor");

    private final String value;

    ActivityType(final String value) {
      this.value = value;
    }
  }

  public static StoppedMessage create(final Suspension suspension) {
    Reason reason;
    if (suspension.getEvent().getBreakpoints().isEmpty()) {
      reason = Reason.step;
    } else {
      reason = Reason.breakpoint;
    }

    assert suspension.getActivity() instanceof Actor : "TODO support threads";

    return new StoppedMessage(reason, suspension.activityId,
        ActivityType.Actor, ""); // TODO: look into additional details that can be provided as text
  }
}
