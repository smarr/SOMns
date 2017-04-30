package tools.debugger.entities;

import com.google.gson.annotations.SerializedName;
import com.oracle.truffle.api.debug.SuspendedEvent;

import som.vm.NotYetImplementedException;
import tools.concurrency.Tags;


// TODO: stepping, is that the right name?
public enum SteppingType {

  @SerializedName("resume")
  RESUME("resume", "Resume Execution", Group.BASIC_CONTROLS, "play", null) {
    @Override
    public void process(final SuspendedEvent event) {
      event.prepareContinue();
    }
  },

  @SerializedName("pause")
  PAUSE("pause", "Pause Execution", Group.BASIC_CONTROLS, "pause", null) {
    @Override
    public void process(final SuspendedEvent event) {
      // TODO: at this point, we don't have an `event`???!!!
      throw new NotYetImplementedException();
    }
  },

  @SerializedName("stop")
  STOP("stop", "stop", Group.BASIC_CONTROLS, "stop", null) {
    @Override
    public void process(final SuspendedEvent event) {
      event.prepareKill();
    }
  },

  @SerializedName("stepInto")
  STEP_INTO("stepInto", "Step Into", Group.LOCAL_STEPPING, "arrow-down", null) {
    @Override
    public void process(final SuspendedEvent event) {
      event.prepareStepInto(1);
    }
  },

  @SerializedName("stepOver")
  STEP_OVER("stepOver", "Step Over", Group.LOCAL_STEPPING, "arrow-right", null) {
    @Override
    public void process(final SuspendedEvent event) {
      event.prepareStepOver(1);
    }
  },

  @SerializedName("return")
  STEP_RETURN("return", "Return from Method", Group.LOCAL_STEPPING, "arrow-left", null) {
    @Override
    public void process(final SuspendedEvent event) {
      event.prepareStepOut();
    }
  },

  STEP_TO_RECEIVER_MESSAGE("todo", "todo", Group.ACTOR_STEPPING, "arrow-right", null) { @Override public void process(final SuspendedEvent event) { /* TODO */ } },
  STEP_TO_PROMISE_RESOLUTION("todo", "todo", Group.ACTOR_STEPPING, "arrow-right", null) { @Override public void process(final SuspendedEvent event) { /* TODO */ } },
  STEP_TO_NEXT_MESSAGE("todo", "todo", Group.ACTOR_STEPPING, "arrow-right", null) { @Override public void process(final SuspendedEvent event) { /* TODO */ } },
  STEP_RETURN_TO_PROMISE_RESOLUTION("todo", "todo", Group.ACTOR_STEPPING, "arrow-right", null) { @Override public void process(final SuspendedEvent event) { /* TODO */ } };

  public enum Group {
    BASIC_CONTROLS("Basic Controls"),
    LOCAL_STEPPING("Local Stepping"),
    ACTOR_STEPPING("Actor Stepping");

    public final String label;

    Group(final String label) { this.label = label; }
  }

  public final String name;
  public final String label;
  public final Group  group;
  public final String icon;

  /** Tag to identify the source sections at which this step operation makes sense.
      If no tags are given, it is assumed the operation is always valid. */
  public final Class<? extends Tags>[] applicableTo;

  SteppingType(final String name, final String label, final Group group, final String icon,
      final Class<? extends Tags>[] applicableTo) {
    this.name  = name;
    this.label = label;
    this.group = group;
    this.icon  = icon;
    this.applicableTo = applicableTo;
  }

  public abstract void process(SuspendedEvent event);
}
