package som.interpreter.actors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SomLanguage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.Activity;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import tools.concurrency.KomposTrace;
import tools.concurrency.TracingActivityThread;
import tools.debugger.entities.PassiveEntityType;
import tools.dym.DynamicMetrics;
import tools.replay.PassiveEntityWithEvents;
import tools.replay.ReplayRecord;
import tools.replay.TraceRecord;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;
import tools.snapshot.nodes.PromiseSerializationNodesFactory.PromiseSerializationNodeFactory;
import tools.snapshot.nodes.PromiseSerializationNodesFactory.ResolverSerializationNodeFactory;


public class SPromise extends SObjectWithClass {
  private static final AtomicLong numPromises  = DynamicMetrics.createLong("Num.Promises");
  private static final AtomicLong numResolvers = DynamicMetrics.createLong("Num.Resolvers");

  private static final AtomicLong numRegisteredWhenResolved =
      DynamicMetrics.createLong("Num.Registered.WhenResolved");
  private static final AtomicLong numRegisteredOnError      =
      DynamicMetrics.createLong("Num.Registered.OnError");
  private static final AtomicLong numScheduledWhenResolved  =
      DynamicMetrics.createLong("Num.Scheduled.WhenResolved");
  private static final AtomicLong numScheduledOnError       =
      DynamicMetrics.createLong("Num.Scheduled.OnError");

  public enum Resolution {
    UNRESOLVED, ERRONEOUS, SUCCESSFUL, CHAINED
  }

  @CompilationFinal private static SClass promiseClass;

  public static SPromise createPromise(final Actor owner,
      final boolean haltOnResolver, final boolean haltOnResolution,
      final SourceSection section) {
    if (VmSettings.DYNAMIC_METRICS) {
      numPromises.getAndIncrement();
    }

    if (VmSettings.KOMPOS_TRACING) {
      return new SMedeorPromise(owner, haltOnResolver, haltOnResolution, section);
    } else if (VmSettings.REPLAY) {
      return new SReplayPromise(owner, haltOnResolver, haltOnResolution);
    } else if (VmSettings.ACTOR_TRACING) {
      return new STracingPromise(owner, haltOnResolver, haltOnResolution);
    } else {
      return new SPromise(owner, haltOnResolver, haltOnResolution);
    }
  }

  /** Used by snapshot deserialization. */
  public static SPromise createResolved(final Actor owner, final Object value,
      final Resolution state) {
    assert VmSettings.SNAPSHOTS_ENABLED;
    SPromise prom = createPromise(owner, false, false, null);
    prom.resolutionState = state;
    prom.value = value;
    return prom;
  }

  // THREAD-SAFETY: these fields are subject to race conditions and should only
  // be accessed when under the SPromise(this) lock
  // currently, we minimize locking by first setting the result
  // value and resolved flag, and then release the lock again
  // this makes sure that the promise owner directly schedules
  // call backs, and does not block the resolver to schedule
  // call backs here either. After resolving the future,
  // whenResolved and whenBroken should only be accessed by the
  // resolver actor
  protected PromiseMessage            whenResolved;
  protected ArrayList<PromiseMessage> whenResolvedExt;
  protected PromiseMessage            onError;
  protected ArrayList<PromiseMessage> onErrorExt;

  protected SPromise            chainedPromise;
  protected ArrayList<SPromise> chainedPromiseExt;

  protected Object     value;
  protected Resolution resolutionState;

  /** The owner of this promise, on which all call backs are scheduled. */
  protected final Actor owner;

  /**
   * Trigger breakpoint at the point where the promise is resolved with a value.
   */
  private final boolean haltOnResolver;

  /**
   * Trigger a breakpoint when executing a resolution callback.
   */
  private boolean haltOnResolution;

  protected SPromise(final Actor owner, final boolean haltOnResolver,
      final boolean haltOnResolution) {
    super(promiseClass, promiseClass.getInstanceFactory());
    assert owner != null;
    this.owner = owner;
    this.haltOnResolver = haltOnResolver;
    this.haltOnResolution = haltOnResolution;

    resolutionState = Resolution.UNRESOLVED;
    assert promiseClass != null;
  }

  @Override
  public String toString() {
    String r = "Promise[" + owner.toString();
    r += ", " + resolutionState.name();
    return r + (value == null ? "" : ", " + value.toString()) + "]";
  }

  @Override
  public final boolean isValue() {
    return false;
  }

  /**
   * Do not use for things other than serializing Promises.
   *
   * @return the value this promise was resolved to
   */
  public final Object getValueForSnapshot() {
    assert VmSettings.SNAPSHOTS_ENABLED;
    assert resolutionState == Resolution.SUCCESSFUL || resolutionState == Resolution.ERRONEOUS;
    assert value != null;
    return value;
  }

  /**
   * Do not use for things other than deserializing Promises.
   * This is necessary for circular object graphs.
   */
  public final void setValueFromSnapshot(final Object value) {
    assert VmSettings.SNAPSHOTS_ENABLED;
    assert resolutionState == Resolution.SUCCESSFUL || resolutionState == Resolution.ERRONEOUS;
    assert value != null;
    this.value = value;
  }

  public long getPromiseId() {
    return 0;
  }

  public final Actor getOwner() {
    return owner;
  }

  public static void setSOMClass(final SClass cls) {
    assert promiseClass == null || cls == null;
    promiseClass = cls;
    if (VmSettings.SNAPSHOTS_ENABLED) {
      ClassFactory group = promiseClass.getInstanceFactory();
      group.getSerializer().replace(PromiseSerializationNodeFactory.create(group));
    }
  }

  public static SClass getPromiseClass() {
    assert promiseClass != null;
    return promiseClass;
  }

  public final synchronized SPromise getChainedPromiseFor(final Actor target,
      final RecordOneEvent recordPromiseChaining) {
    SPromise remote = SPromise.createPromise(target, haltOnResolver,
        haltOnResolution, null);
    if (VmSettings.KOMPOS_TRACING) {
      KomposTrace.promiseChained(getPromiseId(), remote.getPromiseId());
    }

    if (VmSettings.REPLAY) {
      ReplayRecord npr = TracingActivityThread.currentThread().getActivity()
                                              .peekNextReplayEvent();
      if (npr.type == TraceRecord.PROMISE_CHAINED) {
        ((SReplayPromise) this).registerChainedPromiseReplay((SReplayPromise) remote);
        return remote;
      }
    }

    if (isCompleted()) {
      remote.value = value;
      remote.resolutionState = resolutionState;
    } else {

      if (VmSettings.ACTOR_TRACING) {
        recordPromiseChaining.record(((STracingPromise) this).version);
        ((STracingPromise) this).version++;
      }
      if (VmSettings.REPLAY) {
        ((SReplayPromise) remote).untrackedResolution = true;
      }

      addChainedPromise(remote);
    }
    return remote;
  }

  public final void registerWhenResolvedUnsynced(final PromiseMessage msg) {
    if (VmSettings.DYNAMIC_METRICS) {
      numRegisteredWhenResolved.incrementAndGet();
    }
    if (whenResolved == null) {
      whenResolved = msg;
    } else {
      registerMoreWhenResolved(msg);
    }
  }

  @TruffleBoundary
  private void registerMoreWhenResolved(final PromiseMessage msg) {
    if (whenResolvedExt == null) {
      whenResolvedExt = new ArrayList<>(2);
    }
    whenResolvedExt.add(msg);
  }

  public final void registerOnErrorUnsynced(final PromiseMessage msg) {
    if (VmSettings.DYNAMIC_METRICS) {
      numRegisteredOnError.incrementAndGet();
    }
    if (onError == null) {
      onError = msg;
    } else {
      registerMoreOnError(msg);
    }
  }

  @TruffleBoundary
  private void registerMoreOnError(final PromiseMessage msg) {
    if (onErrorExt == null) {
      onErrorExt = new ArrayList<>(2);
    }
    onErrorExt.add(msg);
  }

  protected final void scheduleCallbacksOnResolution(final Object result,
      final PromiseMessage msg, final Actor current,
      final ForkJoinPool actorPool, final boolean haltOnResolution) {
    // when a promise is resolved, we need to schedule all the
    // #whenResolved:/#onError:/... callbacks msgs as well as all eventual send
    // msgs to the promise

    assert owner != null;
    msg.resolve(result, owner, current);

    // if the promise had a haltOnResolution,
    // the message needs to break on receive
    if (haltOnResolution) {
      msg.enableHaltOnReceive();
    }
    msg.getTarget().send(msg, actorPool);
  }

  public final synchronized void addChainedPromise(final SPromise remote) {
    assert remote != null;

    remote.resolutionState = Resolution.CHAINED;
    if (chainedPromise == null) {
      chainedPromise = remote;
    } else {
      addMoreChainedPromises(remote);
    }
  }

  @TruffleBoundary
  private void addMoreChainedPromises(final SPromise promise) {
    if (chainedPromiseExt == null) {
      chainedPromiseExt = new ArrayList<>(2);
    }
    chainedPromiseExt.add(promise);
  }

  /**
   * @return true, if it has a valid value, either successful or erroneous
   */
  public final synchronized boolean isCompleted() {
    return resolutionState == Resolution.SUCCESSFUL || resolutionState == Resolution.ERRONEOUS;
  }

  public static final boolean isCompleted(final Resolution result) {
    return result == Resolution.SUCCESSFUL || result == Resolution.ERRONEOUS;
  }

  /** Internal Helper, only to be used properly synchronized. */
  public final Resolution getResolutionStateUnsync() {
    return resolutionState;
  }

  public final boolean assertNotCompleted() {
    assert !isCompleted() : "Not sure yet what to do with re-resolving of promises? just ignore it? Error?";
    assert value == null : "If it isn't resolved yet, it shouldn't have a value";
    return true;
  }

  /** Internal Helper, only to be used properly synchronized. */
  final boolean isResolvedUnsync() {
    return resolutionState == Resolution.SUCCESSFUL;
  }

  /** Internal Helper, only to be used properly synchronized. */
  public final boolean isErroredUnsync() {
    return resolutionState == Resolution.ERRONEOUS;
  }

  /** Internal Helper, only to be used properly synchronized. */
  final Object getValueUnsync() {
    return value;
  }

  /** Do not use for things other than serializing Promises, requires synchronization. */
  public PromiseMessage getWhenResolvedUnsync() {
    assert VmSettings.SNAPSHOTS_ENABLED;
    return whenResolved;
  }

  /** Do not use for things other than serializing Promises, requires synchronization. */
  public ArrayList<PromiseMessage> getWhenResolvedExtUnsync() {
    assert VmSettings.SNAPSHOTS_ENABLED;
    return whenResolvedExt;
  }

  /** Do not use for things other than serializing Promises, requires synchronization. */
  public PromiseMessage getOnError() {
    assert VmSettings.SNAPSHOTS_ENABLED;
    return onError;
  }

  /** Do not use for things other than serializing Promises, requires synchronization. */
  public ArrayList<PromiseMessage> getOnErrorExtUnsync() {
    assert VmSettings.SNAPSHOTS_ENABLED;
    return onErrorExt;
  }

  /** Do not use for things other than serializing Promises, requires synchronization. */
  public SPromise getChainedPromiseUnsync() {
    assert VmSettings.SNAPSHOTS_ENABLED;
    return chainedPromise;
  }

  /** Do not use for things other than serializing Promises, requires synchronization. */
  public ArrayList<SPromise> getChainedPromiseExtUnsync() {
    assert VmSettings.SNAPSHOTS_ENABLED;
    return chainedPromiseExt;
  }

  public static class STracingPromise extends SPromise implements PassiveEntityWithEvents {

    protected STracingPromise(final Actor owner, final boolean haltOnResolver,
        final boolean haltOnResolution) {
      super(owner, haltOnResolver, haltOnResolution);
      version = 0;
    }

    protected int version;

    @Override
    public int getNextEventNumber() {
      return version;
    }
  }

  public static class SReplayPromise extends STracingPromise {
    PriorityQueue<PromiseMessage>                     onResolvedReplay;
    PriorityQueue<PromiseMessage>                     onErrorReplay;
    PriorityQueue<SReplayPromise>                     replayChainedPromises;
    HashMap<PromiseMessage, LinkedList<ReplayRecord>> delayedMessages;
    boolean                                           delayed             = false;
    long                                              resolutionversion;
    boolean                                           haltOnResolution;
    Actor                                             resolver;
    Resolution                                        type;
    long                                              priority;
    ValueProfile                                      whenResolvedProfile;
    boolean                                           untrackedResolution = false;
    LinkedList<ReplayRecord>                          eventsForDelayedResolution;

    protected SReplayPromise(final Actor owner, final boolean haltOnResolver,
        final boolean haltOnResolution) {
      super(owner, haltOnResolver, haltOnResolution);
    }

    @TruffleBoundary
    public void handleReplayResolution(final boolean haltOnResolution, final Actor resolver,
        final Resolution type, final ValueProfile whenResolvedProfile) {

      Activity current = TracingActivityThread.currentThread().getActivity();
      if (untrackedResolution) {
        assert this.onResolvedReplay == null;
        assert this.onErrorReplay == null;
        assert this.replayChainedPromises == null;
        assert this.delayedMessages == null;
        assert eventsForDelayedResolution == null;
        return;
      }

      assert this.eventsForDelayedResolution == null;

      ReplayRecord npr = current.peekNextReplayEvent();
      assert npr != null;
      assert npr.type == TraceRecord.PROMISE_RESOLUTION : "was " + npr.type + " in "
          + current.getId() + " for " + this.hashCode();

      if (npr.eventNo != this.version) {
        // delay resolution
        this.delayed = true;
        this.resolutionversion = npr.eventNo;
        this.haltOnResolution = haltOnResolution;
        this.resolver = resolver;
        this.whenResolvedProfile = whenResolvedProfile;
        this.type = type;

        consumeEventsForDelayedResolution();
        return;
      }

      // consume event
      npr = current.getNextReplayEvent();
      assert npr.type == TraceRecord.PROMISE_RESOLUTION;

      PriorityQueue<PromiseMessage> todo = null;

      if (type == Resolution.SUCCESSFUL) {
        todo = onResolvedReplay;
      } else if (type == Resolution.ERRONEOUS) {
        todo = onErrorReplay;
      }

      if (todo != null) {
        while (!todo.isEmpty()) {
          PromiseMessage pm = todo.poll();
          pm.resolve(this.value, owner, (Actor) current);

          npr = current.getNextReplayEvent();
          assert npr.type == TraceRecord.MESSAGE : "was " + npr.type + " in "
              + current.getId();
          pm.setReplayVersion(npr.eventNo);

          if (type == Resolution.SUCCESSFUL) {
            this.registerWhenResolvedUnsynced(pm);
          } else if (type == Resolution.ERRONEOUS) {
            this.registerOnErrorUnsynced(pm);
          }
        }
      }

      resolveChainedPromisesReplay(type, whenResolvedProfile);

      sendDelayedMessages();

      npr = current.getNextReplayEvent();
      assert npr != null;
      assert npr.type == TraceRecord.PROMISE_RESOLUTION_END : "was " + npr.type + " in "
          + current.getId() + " for " + this.hashCode();
    }

    /**
     * consumes all events related to the resolution of this promise from the current
     * activities queue, and stores them inside the promise so they can be used in the delayed
     * resolution. This includes "nested" resolutions of chained Promises with unlimited depth.
     */
    @TruffleBoundary
    public void consumeEventsForDelayedResolution() {
      assert eventsForDelayedResolution == null;
      eventsForDelayedResolution = new LinkedList<ReplayRecord>();
      Activity current = TracingActivityThread.currentThread().getActivity();
      ReplayRecord npr;

      int open = 0;

      do {
        npr = current.getNextReplayEvent();
        switch (npr.type) {
          case MESSAGE:
          case PROMISE_CHAINED:
            assert open > 0;
            break;
          case PROMISE_RESOLUTION:
            open++;
            break;
          case PROMISE_RESOLUTION_END:
            assert open > 0;
            open--;
            break;
          default:
            assert false : " unexpected event: " + npr.type;
        }
        eventsForDelayedResolution.add(npr);
      } while (open > 0);
      assert this.eventsForDelayedResolution.size() >= 2;

    }

    /**
     * Moves Message previously registered with registerOnResolvedReplay() to the regular
     * message storage.
     * Also consumes the send events for those messages, and attaches the version numbers to
     * the message objects.
     */

    @TruffleBoundary
    private void tryPerformDelayedResolution() {
      if (delayed && resolutionversion == version) {
        Activity current = TracingActivityThread.currentThread().getActivity();
        // restore events needed for resolution

        assert this.eventsForDelayedResolution != null;
        assert this.eventsForDelayedResolution.size() >= 2 : ""
            + this.eventsForDelayedResolution.size();
        current.getReplayEventBuffer().addAll(0, this.eventsForDelayedResolution);
        ReplayRecord npr = current.getNextReplayEvent();
        assert npr.type == TraceRecord.PROMISE_RESOLUTION : " was " + npr.type;

        PriorityQueue<PromiseMessage> toSend = null;

        assert type != null;
        if (type == Resolution.ERRONEOUS) {
          toSend = onErrorReplay;
        } else if (type == Resolution.SUCCESSFUL) {
          toSend = onResolvedReplay;
        }

        if (toSend != null) {
          while (!toSend.isEmpty()) {
            PromiseMessage pm = toSend.remove();
            pm.resolve(this.value, owner, resolver);

            npr = current.getNextReplayEvent();
            assert npr.type == TraceRecord.MESSAGE;
            pm.setReplayVersion(npr.eventNo);
            this.scheduleCallbacksOnResolution(this.value, pm, resolver,
                SomLanguage.getCurrent().getVM().getActorPool(),
                haltOnResolution);
          }
        }

        resolveChainedPromisesReplay(this.type, this.whenResolvedProfile);
        sendDelayedMessages();

        npr = current.getNextReplayEvent();
        assert npr.type == TraceRecord.PROMISE_RESOLUTION_END : "was " + npr.type + " in "
            + current.getId();

        this.onResolvedReplay = null;
        this.onErrorReplay = null;
        this.replayChainedPromises = null;
        this.eventsForDelayedResolution = null;
      }
    }

    @TruffleBoundary
    public void registerPromiseMessageWithChainingEvents(final PromiseMessage msg,
        final LinkedList<ReplayRecord> events) {
      if (delayedMessages == null) {
        delayedMessages = new HashMap<>();
      }

      delayedMessages.put(msg, events);
    }

    @TruffleBoundary
    private void sendDelayedMessages() {
      if (delayedMessages != null) {
        Activity current = TracingActivityThread.currentThread().getActivity();
        for (Entry<PromiseMessage, LinkedList<ReplayRecord>> e : delayedMessages.entrySet()) {
          PromiseMessage pm = e.getKey();
          LinkedList<ReplayRecord> events = e.getValue();
          current.getReplayEventBuffer().addAll(0, events);
          this.scheduleCallbacksOnResolution(value, pm, (Actor) current,
              SomLanguage.getCurrent().getVM().getActorPool(), haltOnResolution);
        }
      }
    }

    public void registerOnErrorReplay(final PromiseMessage msg) {
      if (this.onErrorReplay == null) {
        this.onErrorReplay = new PriorityQueue<>(new Comparator<PromiseMessage>() {
          @Override
          public int compare(final PromiseMessage o1, final PromiseMessage o2) {
            return Long.compare(o1.messageId, o2.messageId);
          };
        });
      }

      this.onErrorReplay.add(msg);
      this.version++;

      tryPerformDelayedResolution();
    }

    /**
     * Stores Message Objects in a priority Queue, to bring the messages in the original order.
     * Use resolveReplay method before resolving promise.
     *
     */
    public void registerOnResolvedReplay(final PromiseMessage msg) {
      if (this.onResolvedReplay == null) {
        this.onResolvedReplay = new PriorityQueue<>(new Comparator<PromiseMessage>() {
          @Override
          public int compare(final PromiseMessage o1, final PromiseMessage o2) {
            return Long.compare(o1.messageId, o2.messageId);
          };
        });
      }

      this.onResolvedReplay.add(msg);
      this.version++;

      tryPerformDelayedResolution();
    }

    public void registerChainedPromiseReplay(final SReplayPromise prom) {
      ReplayRecord npr = TracingActivityThread.currentThread().getActivity()
                                              .getNextReplayEvent();
      assert npr.type == TraceRecord.PROMISE_CHAINED;
      prom.priority = npr.eventNo;

      if (this.replayChainedPromises == null) {
        this.replayChainedPromises = new PriorityQueue<>(new Comparator<SReplayPromise>() {
          @Override
          public int compare(final SReplayPromise o1, final SReplayPromise o2) {
            return Long.compare(o1.priority, o2.priority);
          };
        });
      }

      this.replayChainedPromises.add(prom);
      this.version++;

      tryPerformDelayedResolution();
    }

    private void resolveChainedPromisesReplay(final Resolution type,
        final ValueProfile whenResolvedProfile) {

      if (replayChainedPromises != null) {
        while (!replayChainedPromises.isEmpty()) {
          SReplayPromise rp = replayChainedPromises.remove();
          Object wrapped = rp.owner.wrapForUse(value, resolver, null);

          SResolver.resolveAndTriggerListenersUnsynced(type, value, wrapped, rp, resolver,
              SomLanguage.getCurrent().getVM().getActorPool(), haltOnResolution,
              whenResolvedProfile, null, null);
        }
      }
    }
  }

  public static final class SMedeorPromise extends SPromise {
    protected final long promiseId;
    protected long       resolvingActor;

    protected SMedeorPromise(final Actor owner, final boolean haltOnResolver,
        final boolean haltOnResolution, final SourceSection section) {
      super(owner, haltOnResolver, haltOnResolution);
      promiseId = TracingActivityThread.newEntityId();
      KomposTrace.passiveEntityCreation(
          PassiveEntityType.PROMISE, promiseId, section);
    }

    public long getResolvingActor() {
      if (!VmSettings.TRACK_SNAPSHOT_ENTITIES) {
        assert isCompleted();
      }
      return resolvingActor;
    }

    @Override
    public long getPromiseId() {
      return promiseId;
    }
  }

  public static SResolver createResolver(final SPromise promise) {
    if (VmSettings.DYNAMIC_METRICS) {
      numResolvers.getAndIncrement();
    }

    return new SResolver(promise);
  }

  public static final class SResolver extends SObjectWithClass {
    @CompilationFinal private static SClass resolverClass;

    protected final SPromise promise;

    private SResolver(final SPromise promise) {
      super(resolverClass, resolverClass.getInstanceFactory());
      this.promise = promise;
      assert resolverClass != null;
    }

    public SPromise getPromise() {
      return promise;
    }

    @Override
    public boolean isValue() {
      return true;
    }

    @Override
    public String toString() {
      return "Resolver[" + promise.toString() + "]";
    }

    public static void setSOMClass(final SClass cls) {
      assert resolverClass == null || cls == null;
      resolverClass = cls;

      if (VmSettings.SNAPSHOTS_ENABLED) {
        ClassFactory group = resolverClass.getInstanceFactory();
        group.getSerializer().replace(ResolverSerializationNodeFactory.create(group));
      }
    }

    public static SClass getResolverClass() {
      assert resolverClass != null;
      return resolverClass;
    }

    public boolean assertNotCompleted() {
      return promise.assertNotCompleted();
    }

    /**
     * Handles the case when a promise is resolved with a proper value
     * and previously has been chained with other promises.
     */
    // TODO: solve the TODO and then remove the TruffleBoundary, this might even need to go
    // into a node
    @TruffleBoundary
    protected static void resolveChainedPromisesUnsync(final Resolution type,
        final SPromise promise, final Object result, final Actor current,
        final ForkJoinPool actorPool, final boolean haltOnResolution,
        final ValueProfile whenResolvedProfile, final RecordOneEvent record,
        final RecordOneEvent recordStop) {
      // TODO: we should change the implementation of chained promises to
      // always move all the handlers to the other promise, then we
      // don't need to worry about traversing the chain, which can
      // lead to a stack overflow.
      // TODO: restore 10000 as parameter in testAsyncDeeplyChainedResolution
      if (promise.chainedPromise != null) {
        SPromise chainedPromise = promise.chainedPromise;
        promise.chainedPromise = null;
        Object wrapped =
            WrapReferenceNode.wrapForUse(chainedPromise.owner, result, current, null);
        resolveAndTriggerListenersUnsynced(type, result, wrapped,
            chainedPromise, current, actorPool,
            chainedPromise.haltOnResolution, whenResolvedProfile, record, recordStop);
        resolveMoreChainedPromisesUnsynced(type, promise, result, current,
            actorPool, haltOnResolution, whenResolvedProfile, record, recordStop);
      }
    }

    /**
     * Resolve the other promises that has been chained to the first promise.
     */
    @TruffleBoundary
    private static void resolveMoreChainedPromisesUnsynced(final Resolution type,
        final SPromise promise, final Object result, final Actor current,
        final ForkJoinPool actorPool, final boolean haltOnResolution,
        final ValueProfile whenResolvedProfile, final RecordOneEvent record,
        final RecordOneEvent recordStop) {
      if (promise.chainedPromiseExt != null) {
        ArrayList<SPromise> chainedPromiseExt = promise.chainedPromiseExt;
        promise.chainedPromiseExt = null;

        for (SPromise p : chainedPromiseExt) {
          Object wrapped = WrapReferenceNode.wrapForUse(p.owner, result, current, null);
          resolveAndTriggerListenersUnsynced(type, result, wrapped, p, current,
              actorPool, haltOnResolution, whenResolvedProfile, record, recordStop);
        }
      }
    }

    /**
     * Resolution of a promise with a proper value.
     * All callbacks for this promise are going to be scheduled.
     * If the promise was chained with other promises, the chained promises are also resolved.
     */
    protected static void resolveAndTriggerListenersUnsynced(final Resolution type,
        final Object result, final Object wrapped, final SPromise p, final Actor current,
        final ForkJoinPool actorPool, final boolean haltOnResolution,
        final ValueProfile whenResolvedProfile, final RecordOneEvent tracePromiseResolution2,
        final RecordOneEvent tracePromiseResolutionEnd2) {
      assert !(result instanceof SPromise);

      if (VmSettings.KOMPOS_TRACING) {
        if (type == Resolution.SUCCESSFUL && p.resolutionState != Resolution.CHAINED) {
          KomposTrace.promiseResolution(p.getPromiseId(), result);
        } else if (type == Resolution.ERRONEOUS) {
          KomposTrace.promiseError(p.getPromiseId(), result);
        }
      }

      // LOCKING NOTE: we need a synchronization unit that is not the promise,
      // because otherwise we might end up in a deadlock, but we still need to group the
      // scheduling of messages and the propagation of the resolution state, otherwise
      // we might see message ordering issues
      synchronized (wrapped) {
        // LOCKING NOTE: We can split the scheduling out of the synchronized
        // because after resolving the promise, all clients will schedule their
        // callbacks/msg themselves
        synchronized (p) {
          assert p.assertNotCompleted();
          // TODO: is this correct? can we just resolve chained promises like this? this means,
          // their state changes twice. I guess it is ok, not sure about synchronization
          // thought. They are created as 'chained', and then there is the resolute propagation
          // accross chained promisses
          // TODO use a special constructor to create chained promises???
          p.value = wrapped;
          p.resolutionState = type;

          if (VmSettings.REPLAY) {
            ((SReplayPromise) p).handleReplayResolution(haltOnResolution, current, type,
                whenResolvedProfile);
          }
        }

        if (VmSettings.ACTOR_TRACING) {
          tracePromiseResolution2.record(((STracingPromise) p).version);
        }

        if (type == Resolution.SUCCESSFUL) {
          scheduleAllWhenResolvedUnsync(p, result, current, actorPool, haltOnResolution,
              whenResolvedProfile);
        } else {
          assert type == Resolution.ERRONEOUS;
          scheduleAllOnErrorUnsync(p, result, current, actorPool, haltOnResolution);
        }
        resolveChainedPromisesUnsync(type, p, result, current, actorPool, haltOnResolution,
            whenResolvedProfile, tracePromiseResolution2, tracePromiseResolutionEnd2);

        if (VmSettings.ACTOR_TRACING) {
          tracePromiseResolutionEnd2.record(((STracingPromise) p).version);
        }
      }
    }

    /**
     * Schedule all whenResolved callbacks for the promise.
     */
    protected static void scheduleAllWhenResolvedUnsync(final SPromise promise,
        final Object result, final Actor current, final ForkJoinPool actorPool,
        final boolean haltOnResolution, final ValueProfile whenResolvedProfile) {
      if (promise.whenResolved != null) {
        PromiseMessage whenResolved = promise.whenResolved;
        ArrayList<PromiseMessage> whenResolvedExt = promise.whenResolvedExt;
        promise.whenResolved = null;
        promise.whenResolvedExt = null;

        if (VmSettings.DYNAMIC_METRICS) {
          int count = whenResolvedExt == null ? 0 : whenResolvedExt.size();
          numScheduledWhenResolved.addAndGet(1 + count);
        }

        promise.scheduleCallbacksOnResolution(result,
            whenResolvedProfile.profile(whenResolved), current, actorPool, haltOnResolution);
        scheduleExtensions(promise, whenResolvedExt, result, current, actorPool,
            haltOnResolution);
      }
    }

    /**
     * Schedule callbacks from the whenResolvedExt extension array.
     */
    @TruffleBoundary
    private static void scheduleExtensions(final SPromise promise,
        final ArrayList<PromiseMessage> extension,
        final Object result, final Actor current,
        final ForkJoinPool actorPool, final boolean haltOnResolution) {
      if (extension != null) {
        for (int i = 0; i < extension.size(); i++) {
          PromiseMessage callbackOrMsg = extension.get(i);
          promise.scheduleCallbacksOnResolution(result, callbackOrMsg, current,
              actorPool, haltOnResolution);
        }
      }
    }

    /**
     * Schedule all onError callbacks for the promise.
     */
    protected static void scheduleAllOnErrorUnsync(final SPromise promise,
        final Object result, final Actor current,
        final ForkJoinPool actorPool, final boolean haltOnResolution) {
      if (promise.onError != null) {
        PromiseMessage onError = promise.onError;
        ArrayList<PromiseMessage> onErrorExt = promise.onErrorExt;
        promise.onError = null;
        promise.onErrorExt = null;

        if (VmSettings.DYNAMIC_METRICS) {
          int count = onErrorExt == null ? 0 : onErrorExt.size();
          numScheduledOnError.addAndGet(1 + onErrorExt.size());
        }

        promise.scheduleCallbacksOnResolution(result, onError, current, actorPool,
            haltOnResolution);
        scheduleExtensions(promise, onErrorExt, result, current, actorPool, haltOnResolution);
      }
    }
  }

  @CompilationFinal public static SClass pairClass;

  public static void setPairClass(final SClass cls) {
    assert pairClass == null || cls == null;
    pairClass = cls;
  }

  boolean getHaltOnResolver() {
    return haltOnResolver;
  }

  boolean getHaltOnResolution() {
    return haltOnResolution;
  }

  public void enableHaltOnResolution() {
    haltOnResolution = true;
  }
}
