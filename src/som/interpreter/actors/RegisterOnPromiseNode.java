package som.interpreter.actors;

import java.util.LinkedList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

import som.interpreter.actors.EventualMessage.AbstractPromiseSendMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise.SReplayPromise;
import som.interpreter.actors.SPromise.STracingPromise;
import som.interpreter.SArguments;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import tools.debugger.asyncstacktraces.ShadowStackEntry;
import tools.dym.DynamicMetrics;
import tools.replay.ReplayRecord;
import tools.replay.TraceRecord;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;

import java.util.concurrent.ForkJoinPool;


public abstract class RegisterOnPromiseNode {
  private static final AtomicLong numScheduledWhenResolved =
      DynamicMetrics.createLong("Num.Scheduled.WhenResolved");
  private static final AtomicLong numScheduledOnError      =
      DynamicMetrics.createLong("Num.Scheduled.OnError");

  public static final class RegisterWhenResolved extends Node {
    @Child protected SchedulePromiseHandlerNode schedule;
    @Child protected RecordOneEvent             promiseMsgSend;

    public RegisterWhenResolved(final ForkJoinPool actorPool) {
      schedule = SchedulePromiseHandlerNodeGen.create(actorPool);
      if (VmSettings.SENDER_SIDE_TRACING) {
        promiseMsgSend = new RecordOneEvent(TraceRecord.PROMISE_MESSAGE);
      }
    }

    public void register(final VirtualFrame frame, final SPromise promise, final PromiseMessage msg,
        final Actor current) {

      Object promiseValue;
      // LOCKING NOTE: this handling of the check for resolution and the
      // registering of the callback/msg needs to be done in the same step
      // otherwise, we are opening a race between completion and processing
      // of handlers. We can split the case for a resolved promise out, because
      // we need to schedule the callback/msg directly anyway

      synchronized (promise) {
        if (VmSettings.SENDER_SIDE_REPLAY) {
          ReplayRecord npr = current.peekNextReplayEvent();
          if (npr.type == TraceRecord.PROMISE_CHAINED && !promise.isResolvedUnsync()) {
            npr = current.getNextReplayEvent();

            assert msg instanceof AbstractPromiseSendMessage;
            LinkedList<ReplayRecord> events = new LinkedList<>();
            while (npr.type == TraceRecord.PROMISE_CHAINED) {
              events.add(npr);
              npr = current.getNextReplayEvent();
            }
            assert npr.type == TraceRecord.MESSAGE;
            msg.messageId = npr.eventNo;
            ((SReplayPromise) promise).registerPromiseMessageWithChainingEvents(msg, events);
            return;
          }

          assert npr.type == TraceRecord.PROMISE_MESSAGE
              || npr.type == TraceRecord.MESSAGE
              || npr.type == TraceRecord.PROMISE_CHAINED : "was: " + npr.type.name();

          if (npr.type == TraceRecord.PROMISE_MESSAGE) {
            current.getNextReplayEvent();
            msg.messageId = npr.eventNo;
            ((SReplayPromise) promise).registerOnResolvedReplay(msg);
            return;
          }
        }

        if (!promise.isResolvedUnsync()) {
          if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
            // get info about the resolution context from the promise,
            // we want to know where it was resolved, where the value is coming from
            for (Object obj: msg.args) {
              boolean promiseComplete = (obj instanceof SPromise) && ((SPromise) promise).isCompleted();
//              boolean promiseChained = (obj instanceof SPromise) && !((SPromise) promise).isCompleted();
              if (obj instanceof SBlock || promiseComplete) {
                ShadowStackEntry.EntryForPromiseResolution.ResolutionLocation onReceiveLocation =
                        ShadowStackEntry.EntryForPromiseResolution.ResolutionLocation.ON_WHEN_RESOLVED;
//                onReceiveLocation.setArg(msg.getTarget().getId() + " send by actor "+ msg.getSender().getId());
                //for whenResolved blocks or if promise is resolved, then create EntryForPromiseResolution
                ShadowStackEntry resolutionEntry = ShadowStackEntry.createAtPromiseResolution(
                        SArguments.getShadowStackEntry(frame),
                        getParent().getParent(), onReceiveLocation);
                assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || resolutionEntry != null;
                SArguments.setShadowStackEntry(msg.args, resolutionEntry);
              }
//              else if (promiseChained) {
//                ShadowStackEntry entry = (ShadowStackEntry) msg.args[msg.args.length - 1];
//                assert entry != null && entry instanceof ShadowStackEntry.EntryAtMessageSend;
//                ShadowStackEntry shadowStackEntry = SArguments.getShadowStackEntry(frame);
//
////                entry.setPreviousShadowStackEntry(shadowStackEntry);
//
//                System.out.println("-register msg args: "+entry.getSourceSection());
//                System.out.println("shadow: "+shadowStackEntry.getSourceSection());

//                  assert maybeEntry != null && maybeEntry instanceof ShadowStackEntry.EntryForPromiseResolution;
//                  assert args[args.length - 1] instanceof ShadowStackEntry.EntryAtMessageSend;
//                  ShadowStackEntry.EntryAtMessageSend current = (ShadowStackEntry.EntryAtMessageSend) args[args.length - 1];
//                  SArguments.addEntryForPromiseResolution(current, (ShadowStackEntry.EntryForPromiseResolution) maybeEntry);

//              }
            }
          }
            
          if (VmSettings.SENDER_SIDE_REPLAY) {
            ReplayRecord npr = current.getNextReplayEvent();
            assert npr.type == TraceRecord.MESSAGE;
            msg.messageId = npr.eventNo;
          }
            
          if (VmSettings.SENDER_SIDE_TRACING) {
            // This is whenResolved
            promiseMsgSend.record(((STracingPromise) promise).version);
            ((STracingPromise) promise).version++;
          }

          if (promise.isErroredUnsync()) {
            // short cut on error, this promise will never resolve successfully, so,
            // just return promise, don't use isSomehowResolved(), because the other
            // case are not correct
            return;
          }

          promise.registerWhenResolvedUnsynced(msg);
          return;
        } else {
          promiseValue = promise.value;
          assert promiseValue != null;
        }
      }

      // LOCKING NOTE:
      // The schedule.execute() is moved out of the synchronized(promise) block,
      // because it leads to wrapping of arguments, which needs to look promises
      // and thus, could lead to a deadlock.
      //
      // However, we still need to wait until any other thread is done with
      // resolving the promise, thus we lock on the promise's value, which is
      // used to group setting the promise resolution state and processing the
      // message.
      synchronized (promiseValue) {
        if (promise.getHaltOnResolution()) {
          msg.enableHaltOnReceive();
        }
        if (VmSettings.DYNAMIC_METRICS) {
          numScheduledWhenResolved.incrementAndGet();
        }
        schedule.execute(frame, promise, msg, current);
      }
    }
  }

  public static final class RegisterOnError extends Node {
    @Child protected SchedulePromiseHandlerNode schedule;
    @Child protected RecordOneEvent             promiseMsgSend;

    public RegisterOnError(final ForkJoinPool actorPool) {
      this.schedule = SchedulePromiseHandlerNodeGen.create(actorPool);
      if (VmSettings.SENDER_SIDE_TRACING) {
        this.promiseMsgSend = new RecordOneEvent(TraceRecord.PROMISE_MESSAGE);
      }
    }

    public void register(final VirtualFrame frame, final SPromise promise, final PromiseMessage msg,
        final Actor current) {

      Object promiseValue;
      // LOCKING NOTE: this handling of the check for resolution and the
      // registering of the callback/msg needs to be done in the same step
      // otherwise, we are opening a race between completion and processing
      // of handlers. We can split the case for a resolved promise out, because
      // we need to schedule the callback/msg directly anyway
      synchronized (promise) {
        if (VmSettings.SENDER_SIDE_REPLAY) {
          ReplayRecord npr = current.getNextReplayEvent();
          assert npr.type == TraceRecord.PROMISE_MESSAGE
              || npr.type == TraceRecord.MESSAGE;
          msg.messageId = npr.eventNo;

          if (npr.type == TraceRecord.PROMISE_MESSAGE) {
            ((SReplayPromise) promise).registerOnErrorReplay(msg);
            return;
          }
        }

        if (!promise.isErroredUnsync()) {

          if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
            // TODO: I think, we need the info about the resolution context from the promise
            // we want to know where it was resolved, where the value is coming from
            ShadowStackEntry resolutionEntry = ShadowStackEntry.createAtPromiseResolution(
                    SArguments.getShadowStackEntry(frame),
                    getParent().getParent(), ShadowStackEntry.EntryForPromiseResolution.ResolutionLocation.ON_WHEN_RESOLVED_ERROR);
            assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || resolutionEntry != null;
            SArguments.setShadowStackEntry(msg.args, resolutionEntry);
          }
            
          if (VmSettings.SENDER_SIDE_TRACING) {
            // This is whenResolved
            promiseMsgSend.record(((STracingPromise) promise).version);
            ((STracingPromise) promise).version++;
          }

          if (promise.isResolvedUnsync()) {
            // short cut on resolved, this promise will never error, so,
            // just return promise, don't use isSomehowResolved(), because the other
            // case are not correct
            return;
          }

          promise.registerOnErrorUnsynced(msg);
          return;
        } else {
          promiseValue = promise.value;
          assert promiseValue != null;
        }
      }

      // LOCKING NOTE:
      // The schedule.execute() is moved out of the synchronized(promise) block,
      // because it leads to wrapping of arguments, which needs to look promises
      // and thus, could lead to a deadlock.
      //
      // However, we still need to wait until any other thread is done with
      // resolving the promise, thus we lock on the promise's value, which is
      // used to group setting the promise resolution state and processing the
      // message.
      synchronized (promiseValue) {
        if (promise.getHaltOnResolution()) {
          msg.enableHaltOnReceive();
        }
        if (VmSettings.DYNAMIC_METRICS) {
          numScheduledOnError.incrementAndGet();
        }
        schedule.execute(frame, promise, msg, current);
      }
    }
  }
}
