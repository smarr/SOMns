package tools.debugger.message;

import tools.TraceData;
import tools.debugger.entities.ActivityType;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.OutgoingMessage;


@SuppressWarnings("unused")
public final class StoppedMessage extends OutgoingMessage {
  private String  reason;
  private long    activityId;
  private byte    activityType;
  private String  text;
  private boolean allThreadsStopped;

  private StoppedMessage(final Reason reason, final long activityId,
      final ActivityType type, final String text) {
    assert TraceData.isWithinJSIntValueRange(activityId);
    this.reason     = reason.value;
    this.activityId = activityId;
    this.activityType = type.getId();
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

  public static StoppedMessage create(final Suspension suspension) {
    Reason reason;
    if (suspension.getEvent().getBreakpoints().isEmpty()) {
      reason = Reason.step;
    } else {
      reason = Reason.breakpoint;
    }

    ActivityType type = suspension.getActivity().getType();

    // TODO: look into additional details that can be provided as text
    return new StoppedMessage(reason, suspension.activityId, type, "");
  }
}
