package tools.debugger.entities;

import com.google.gson.annotations.SerializedName;
import com.oracle.truffle.api.debug.SuspendedEvent;


public enum SteppingType {

  @SerializedName("stepInto")
  STEP_INTO("stepInto", "step into") {
    @Override
    public void process(final SuspendedEvent event) {
      event.prepareStepInto(1);
    }
  },

  @SerializedName("stepOver")
  STEP_OVER("stepOver", "step over") {
    @Override
    public void process(final SuspendedEvent event) {
      event.prepareStepOver(1);
    }
  },

  @SerializedName("return")
  STEP_RETURN("return", "return") {
    @Override
    public void process(final SuspendedEvent event) {
      event.prepareStepOut();
    }
  },

  @SerializedName("stop")
  STOP("stop", "stop") {
    @Override
    public void process(final SuspendedEvent event) {
      event.prepareKill();
    }
  },

  @SerializedName("resume")
  RESUME("resume", "resume") {
    @Override
    public void process(final SuspendedEvent event) {
      event.prepareContinue();
    }
  },

  STEP_TO_RECEIVER_MESSAGE("todo", "todo") { @Override public void process(final SuspendedEvent event) { /* TODO */ } },
  STEP_TO_PROMISE_RESOLUTION("todo", "todo") { @Override public void process(final SuspendedEvent event) { /* TODO */ } },
  STEP_TO_NEXT_MESSAGE("todo", "todo") { @Override public void process(final SuspendedEvent event) { /* TODO */ } },
  STEP_RETURN_TO_PROMISE_RESOLUTION("todo", "todo") { @Override public void process(final SuspendedEvent event) { /* TODO */ } };

  public final String name;
  public final String label;

  SteppingType(final String name, final String label) {
    this.name  = name;
    this.label = label;
  }

  public abstract void process(SuspendedEvent event);
}
