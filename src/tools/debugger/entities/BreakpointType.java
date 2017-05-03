package tools.debugger.entities;

import com.google.gson.annotations.SerializedName;

import tools.concurrency.Tags;
import tools.concurrency.Tags.AcquireLock;
import tools.concurrency.Tags.ActivityCreation;
import tools.concurrency.Tags.ActivityJoin;
import tools.concurrency.Tags.Atomic;
import tools.concurrency.Tags.ChannelRead;
import tools.concurrency.Tags.ChannelWrite;
import tools.concurrency.Tags.CreatePromisePair;
import tools.concurrency.Tags.EventualMessageSend;
import tools.concurrency.Tags.MethodDeclaration;
import tools.concurrency.Tags.OnError;
import tools.concurrency.Tags.ReleaseLock;
import tools.concurrency.Tags.WhenResolved;
import tools.concurrency.Tags.WhenResolvedOnError;
import tools.debugger.session.Breakpoints;
import tools.debugger.session.SectionBreakpoint;


@SuppressWarnings({"unchecked", "rawtypes"})
public enum BreakpointType {
  @SerializedName("msgSenderBP")
  MSG_SENDER("msgSenderBP", "Message send",
      new Class[] {EventualMessageSend.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateBeforeExpression(bpInfo);
    }
  },

  @SerializedName("msgReceiverBP")
  MSG_RECEIVER("msgReceiverBP", "Message receive",
      new Class[] {EventualMessageSend.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateMessageReceiver(bpInfo);
    }
  },

  /**
   * Breakpoint on the RootTag node of a method, to halt before its execution, if the method was activated
   * asynchronously.
   *
   * <p>The method is identified by the source section info of the breakpoint.
   */
  @SerializedName("asyncMsgBeforeExecBP")
  ASYNC_MSG_BEFORE_EXEC("asyncMsgBeforeExecBP", "Method async before execution",
      new Class[] {MethodDeclaration.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateAsyncBefore(bpInfo);
    }
  },

  /**
   * Breakpoint on the RootTag node of a method, to halt after its execution, if the method was activated
   * asynchronously.
   *
   * <p>The method is identified by the source section info of the breakpoint.
   */
  @SerializedName("asyncMsgAfterExecBP")
  ASYNC_MSG_AFTER_EXEC("asyncMsgAfterExecBP", "Method async after execution",
      new Class[] {MethodDeclaration.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateAsyncAfter(bpInfo);
    }
  },

  @SerializedName("promiseResolverBP")
  PROMISE_RESOLVER("promiseResolverBP", "Promise resolver",
      new Class[] {EventualMessageSend.class, WhenResolved.class,
          WhenResolvedOnError.class, OnError.class, CreatePromisePair.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdatePromiseResolver(bpInfo);
    }
  },

  @SerializedName("promiseResolutionBP")
  PROMISE_RESOLUTION("promiseResolutionBP", "Promise resolution",
      new Class[] {EventualMessageSend.class, WhenResolved.class,
          WhenResolvedOnError.class, OnError.class, CreatePromisePair.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdatePromiseResolution(bpInfo);
    }
  },

  @SerializedName("channelBeforeSendBP")
  CHANNEL_BEFORE_SEND("channelBeforeSendBP", "Before send",
      new Class[] {ChannelWrite.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateBeforeExpression(bpInfo);
    }
  },

  @SerializedName("channelAfterRcvBP")
  CHANNEL_AFTER_RCV("channelAfterRcvBP", "After receive",
      new Class[] {ChannelWrite.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateChannelOpposite(bpInfo);
    }
  },

  @SerializedName("channelBeforeRcvBP")
  CHANNEL_BEFORE_RCV("channelBeforeRcvBP", "Before receive",
      new Class[] {ChannelRead.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateBeforeExpression(bpInfo);
    }
  },

  @SerializedName("channelAfterSendBP")
  CHANNEL_AFTER_SEND("channelAfterSendBP", "After send",
      new Class[] {ChannelRead.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateChannelOpposite(bpInfo);
    }
  },

  @SerializedName("activityCreationBP")
  ACTIVITY_CREATION("activityCreationBP", "Before creation",
      new Class[] {ActivityCreation.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateBeforeExpression(bpInfo);
    }
  },

  @SerializedName("activityOnExecBP")
  ACTIVITY_ON_EXEC("activityOnExecBP", "On execution",
      new Class[] {ActivityCreation.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateActivityOnExec(bpInfo);
    }
  },

  @SerializedName("activityBeforeJoinBP")
  ACTIVITY_BEFORE_JOIN("activityBeforeJoinBP", "Before join",
      new Class[] {ActivityJoin.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateBeforeExpression(bpInfo);
    }
  },

  @SerializedName("activityAfterJoinBP")
  ACTIVITY_AFTER_JOIN("activityAfterJoinBP", "After join",
      new Class[] {ActivityJoin.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateAfterExpression(bpInfo);
    }
  },

  @SerializedName("atomicBeforeBP")
  ATOMIC_BEFORE("atomicBeforeBP", "Before start",
      new Class[] {Atomic.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateBeforeExpression(bpInfo);
    }
  },

  @SerializedName("atomicBeforeCommitBP")
  ATOMIC_BEFORE_COMMIT("atomicBeforeCommitBP", "Before commit",
      new Class[] {Atomic.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateBeforeCommit(bpInfo);
    }
  },

  @SerializedName("atomicAfterCommitBP")
  ATOMIC_AFTER_COMMIT("atomicAfterCommitBP", "After commit",
      new Class[] {Atomic.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateAfterExpression(bpInfo);
    }
  },

  @SerializedName("lockBeforeBP")
  LOCK_BEFORE_ACQUIRE("lockBeforeBP", "Before acquire",
      new Class[] {AcquireLock.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateBeforeExpression(bpInfo);
    }
  },

  @SerializedName("lockAfterBP")
  LOCK_AFTER_ACQUIRE("lockAfterBP", "After acquire",
      new Class[] {AcquireLock.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateAfterExpression(bpInfo);
    }
  },

  @SerializedName("unlockBeforeBP")
  LOCK_BEFORE_RELEASE("unlockBeforeBP", "Before release",
      new Class[] {ReleaseLock.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateBeforeExpression(bpInfo);
    }
  },

  @SerializedName("unlockAfterBP")
  LOCK_AFTER_RELEASE("unlockAfterBP", "After release",
      new Class[] {ReleaseLock.class}) {
    @Override
    public void registerOrUpdate(final Breakpoints bps, final SectionBreakpoint bpInfo) {
      bps.addOrUpdateAfterExpression(bpInfo);
    }
  };

  public final String name;
  public final String label;

  /** Tag to identify source section the breakpoint is applicable to.
      NOTE: There is currently the assumption (in the UI) that only one of the
      tags is on a specific section. */
  public final Class<? extends Tags>[] applicableTo;

  BreakpointType(final String name, final String label, final Class<? extends Tags>[] applicableTo) {
    this.name         = name;
    this.label        = label;
    this.applicableTo = applicableTo;
  }

  public abstract void registerOrUpdate(Breakpoints bps, SectionBreakpoint bpInfo);
}
