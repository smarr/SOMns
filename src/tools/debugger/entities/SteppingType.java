package tools.debugger.entities;

import com.google.gson.annotations.SerializedName;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;

import som.vm.NotYetImplementedException;
import tools.concurrency.Tags;
import tools.concurrency.Tags.ActivityCreation;
import tools.concurrency.Tags.ChannelRead;
import tools.concurrency.Tags.ChannelWrite;
import tools.concurrency.Tags.EventualMessageSend;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.debugger.SteppingStrategy.AfterCommit;
import tools.debugger.SteppingStrategy.IntoSpawn;
import tools.debugger.SteppingStrategy.ReturnFromActivity;
import tools.debugger.SteppingStrategy.ReturnFromTurnToPromiseResolution;
import tools.debugger.SteppingStrategy.ToChannelOpposite;
import tools.debugger.SteppingStrategy.ToMessageReceiver;
import tools.debugger.SteppingStrategy.ToNextCommit;
import tools.debugger.SteppingStrategy.ToNextTransaction;
import tools.debugger.SteppingStrategy.ToNextTurn;
import tools.debugger.SteppingStrategy.ToPromiseResolution;
import tools.debugger.frontend.Suspension;


// TODO: stepping, is that the right name?
@SuppressWarnings({"unchecked", "rawtypes"})
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
      startHandleFrameSkip(susp);
      susp.getEvent().prepareStepInto(1);
      completeHandleFrameSkip(susp);
    }
  },

  @SerializedName("stepOver")
  STEP_OVER("stepOver", "Step Over", Group.LOCAL_STEPPING, "arrow-right", null) {
    @Override
    public void process(final Suspension susp) {
      startHandleFrameSkip(susp);
      susp.getEvent().prepareStepOver(1);
      completeHandleFrameSkip(susp);
    }
  },

  @SerializedName("return")
  STEP_RETURN("return", "Return from Method", Group.LOCAL_STEPPING, "arrow-left", null) {
    @Override
    public void process(final Suspension susp) {
      startHandleFrameSkip(susp);
      susp.getEvent().prepareStepOut();
      completeHandleFrameSkip(susp);
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

  @SerializedName("stepToNextTx")
  STEP_TO_NEXT_TX("stepToNextTx", "Step to next Transaction", Group.ACTIVITY_STEPPING, "arrow-right", null, null) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareContinue();
      susp.getActivityThread().setSteppingStrategy(new ToNextTransaction());
    }
  },

  @SerializedName("stepToCommit")
  STEP_TO_COMMIT("stepToCommit", "Step to Commit", Group.TX_STEPPING, "arrow-right",
      null, null, new EntityType[] {EntityType.TRANSACTION}) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareContinue();
      susp.getActivityThread().setSteppingStrategy(new ToNextCommit());

    }
  },

  @SerializedName("stepAfterCommit")
  STEP_AFTER_COMMIT("stepAfterCommit", "Complete Transaction", Group.TX_STEPPING, "arrow-left",
      null, null, new EntityType[] {EntityType.TRANSACTION}) {
    @Override
    public void process(final Suspension susp) {
      susp.getEvent().prepareContinue();
      susp.getActivityThread().setSteppingStrategy(new AfterCommit());
    }
  },

  @SerializedName("stepToMessageRcvr")
  STEP_TO_RECEIVER_MESSAGE("stepToMessageRcvr", "Step to Msg Receiver", Group.ACTOR_STEPPING, "msg-open", new Class[] {EventualMessageSend.class}) {
    @Override public void process(final Suspension susp) {
      susp.getEvent().prepareStepOver(1);
      susp.getActivityThread().setSteppingStrategy(new ToMessageReceiver());
      }
    },

  @SerializedName("stepToPromiseResolution")
  STEP_TO_PROMISE_RESOLUTION("stepToPromiseResolution", "Step to Promise Resolution", Group.ACTOR_STEPPING, "msg-white", new Class[] {ExpressionBreakpoint.class}, new ActivityType[] {ActivityType.ACTOR}) {
      @Override public void process(final Suspension susp) {
       susp.getEvent().prepareStepOver(1);
       susp.getActivityThread().setSteppingStrategy(new ToPromiseResolution());
    }
  },

  @SerializedName("stepToNextTurn")
  STEP_TO_NEXT_TURN("stepToNextTurn", "Step to Next Turn", Group.ACTOR_STEPPING, "msg-close", null, new ActivityType[] {ActivityType.ACTOR}) {
    @Override public void process(final Suspension susp) {
      susp.getEvent().prepareStepOver(1);
      susp.getActivityThread().setSteppingStrategy(new ToNextTurn());
    }
  },

  @SerializedName("stepReturnFromTurnToPromiseResolution")
  STEP_RETURN_FROM_TURN_TO_PROMISE_RESOLUTION("stepReturnFromTurnToPromiseResolution", "Step Return From Turn to Promise Resolution", Group.ACTOR_STEPPING, "msg-embedded", new Class[] {StatementTag.class}, new ActivityType[] {ActivityType.ACTOR}) {
    @Override public void process(final Suspension susp) {
      susp.getEvent().prepareStepOver(1);
      susp.getActivityThread().setSteppingStrategy(new ReturnFromTurnToPromiseResolution());
    }
  };

  public enum Group {
    BASIC_CONTROLS("Basic Controls"),
    LOCAL_STEPPING("Local Stepping"),
    ACTIVITY_STEPPING("Activity Stepping"),
    ACTOR_STEPPING("Actor Stepping"),
    PROCESS_STEPPING("Process Stepping"),
    TX_STEPPING("Transaction Stepping");

    public final String label;

    Group(final String label) { this.label = label; }
  }

  private static void startHandleFrameSkip(final Suspension susp) {
    if (susp.getFrameSkipCount() > 0) {
      susp.getEvent().startComposedStepping();
      for (int i = 0; i < susp.getFrameSkipCount(); i += 1) {
        susp.getEvent().prepareStepOut();
      }
    }
  }

  private static void completeHandleFrameSkip(final Suspension susp) {
    if (susp.getFrameSkipCount() > 0) {
      susp.getEvent().prepareComposedStepping();
    }
  }

  public final String name;
  public final String label;
  public final Group  group;
  public final String icon;

  public final ActivityType[] forActivities;

  /** Tag to identify the source sections at which this step operation makes sense.
      If no tags are given, it is assumed the operation is always valid. */
  public final Class<? extends Tags>[] applicableTo;

  /** Stepping operation is only available when in the dynamic scope of the given entity.
      If no entity types are given, it is assumed the operation is always valid. */
  public final EntityType[] inScope;

  SteppingType(final String name, final String label, final Group group, final String icon,
      final Class<? extends Tags>[] applicableTo) {
    this(name, label, group, icon, applicableTo, null, null);
  }

  SteppingType(final String name, final String label, final Group group, final String icon,
      final Class<? extends Tags>[] applicableTo, final ActivityType[] forActivities) {
    this(name, label, group, icon, applicableTo, forActivities, null);
  }

  SteppingType(final String name, final String label, final Group group, final String icon,
      final Class<? extends Tags>[] applicableTo, final ActivityType[] forActivities, final EntityType[] inScope) {
    this.name  = name;
    this.label = label;
    this.group = group;
    this.icon  = icon;
    this.applicableTo  = applicableTo;
    this.forActivities = forActivities;
    this.inScope = inScope;
  }

  public abstract void process(Suspension susp);
}
