package tools.debugger.entities;

import com.google.gson.annotations.SerializedName;

import som.vm.NotYetImplementedException;
import tools.concurrency.Tags;
import tools.concurrency.Tags.ActivityCreation;
import tools.concurrency.Tags.ChannelRead;
import tools.concurrency.Tags.ChannelWrite;
import tools.debugger.SteppingStrategy.IntoSpawn;
import tools.debugger.SteppingStrategy.ReturnFromActivity;
import tools.debugger.SteppingStrategy.ToChannelOpposite;
import tools.debugger.frontend.Suspension;


// TODO: stepping, is that the right name?
@SuppressWarnings("unchecked")
public enum SteppingType {

  @SerializedName("resume")
  RESUME("resume", "Resume Execution", Group.BASIC_CONTROLS, "play", null) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareContinue();
    }
  },

  @SerializedName("pause")
  PAUSE("pause", "Pause Execution", Group.BASIC_CONTROLS, "pause", null) {
    @Override
    public void process(final Suspension susp) {
      // TODO: at this point, we don't have an `event`???!!!
      throw new NotYetImplementedException();
    }
  },

  @SerializedName("stop")
  STOP("stop", "stop", Group.BASIC_CONTROLS, "stop", null) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareKill();
    }
  },

  @SerializedName("stepInto")
  STEP_INTO("stepInto", "Step Into", Group.LOCAL_STEPPING, "arrow-down", null) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareStepInto(1);
    }
  },

  @SerializedName("stepOver")
  STEP_OVER("stepOver", "Step Over", Group.LOCAL_STEPPING, "arrow-right", null) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareStepOver(1);
    }
  },

  @SerializedName("return")
  STEP_RETURN("return", "Return from Method", Group.LOCAL_STEPPING, "arrow-left", null) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareStepOut();
    }
  },

  @SerializedName("stepIntoActivity")
  STEP_INTO_ACTIVITY("stepIntoActivity", "Step into Activity", Group.ACTIVITY_STEPPING, "arrow-down", new Class[] {ActivityCreation.class}) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareStepOver(1);
      susp.getActivityThread().setSteppingStrategy(new IntoSpawn());
    }
  },

  @SerializedName("returnFromActivity")
  RETURN_FROM_ACTIVITY("returnFromActivity", "Return from Activity", Group.ACTIVITY_STEPPING, "arrow-left",
      null, new ActivityType[] {ActivityType.PROCESS, ActivityType.TASK, ActivityType.THREAD}) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareContinue();
      susp.getActivityThread().setSteppingStrategy(new ReturnFromActivity());
    }
  },


  @SerializedName("stepToChannelRcvr")
  STEP_TO_CHANNEL_RCVR("stepToChannelRcvr", "Step to Receiver", Group.PROCESS_STEPPING, "arrow-right", new Class[] {ChannelWrite.class}) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareStepOver(1);
      susp.getActivityThread().setSteppingStrategy(new ToChannelOpposite());
    }
  },

  @SerializedName("stepToChannelSender")
  STEP_TO_CHANNEL_SENDER("stepToChannelSender", "Step to Sender", Group.PROCESS_STEPPING, "arrow-left", new Class[] {ChannelRead.class}) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareStepInto(1);
      susp.getActivityThread().setSteppingStrategy(new ToChannelOpposite());
    }
  },


  STEP_TO_RECEIVER_MESSAGE("todo",   "todo", Group.ACTOR_STEPPING, "arrow-right", null) { @Override public void process(final Suspension susp) { /* TODO */ } },
  STEP_TO_PROMISE_RESOLUTION("todo", "todo", Group.ACTOR_STEPPING, "arrow-right", null) { @Override public void process(final Suspension susp) { /* TODO */ } },
  STEP_TO_NEXT_MESSAGE("todo",       "todo", Group.ACTOR_STEPPING, "arrow-right", null) { @Override public void process(final Suspension susp) { /* TODO */ } },
  STEP_RETURN_TO_PROMISE_RESOLUTION("todo", "todo", Group.ACTOR_STEPPING, "arrow-right", null) { @Override public void process(final Suspension susp) { /* TODO */ } };

  public enum Group {
    BASIC_CONTROLS("Basic Controls"),
    LOCAL_STEPPING("Local Stepping"),
    ACTIVITY_STEPPING("Activity Stepping"),
    ACTOR_STEPPING("Actor Stepping"),
    PROCESS_STEPPING("Process Stepping");

    public final String label;

    Group(final String label) { this.label = label; }
  }

  public final String name;
  public final String label;
  public final Group  group;
  public final String icon;
  public final ActivityType[] forActivities;

  /** Tag to identify the source sections at which this step operation makes sense.
      If no tags are given, it is assumed the operation is always valid. */
  public final Class<? extends Tags>[] applicableTo;

  SteppingType(final String name, final String label, final Group group, final String icon,
      final Class<? extends Tags>[] applicableTo) {
    this(name, label, group, icon, applicableTo, null);
  }

  SteppingType(final String name, final String label, final Group group, final String icon,
      final Class<? extends Tags>[] applicableTo, final ActivityType[] forActivities) {
    this.name  = name;
    this.label = label;
    this.group = group;
    this.icon  = icon;
    this.applicableTo  = applicableTo;
    this.forActivities = forActivities;
  }

  public abstract void process(Suspension susp);
}
