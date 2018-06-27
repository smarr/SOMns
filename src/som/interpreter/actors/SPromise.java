package som.interpreter.actors;

import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import tools.concurrency.MedeorTrace;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.TracingActor;
import tools.debugger.entities.PassiveEntityType;


public class SPromise extends SObjectWithClass {
  public enum Resolution {
    UNRESOLVED, ERRONEOUS, SUCCESSFUL, CHAINED
  }

  @CompilationFinal private static SClass promiseClass;

  public static SPromise createPromise(final Actor owner,
      final boolean haltOnResolver, final boolean haltOnResolution,
      final SourceSection section) {
    if (VmSettings.MEDEOR_TRACING) {
      return new SMedeorPromise(owner, haltOnResolver, haltOnResolution, section);
    } else if (VmSettings.ACTOR_TRACING || VmSettings.REPLAY) {
      return new STracingPromise(owner, haltOnResolver, haltOnResolution);
    } else {
      return new SPromise(owner, haltOnResolver, haltOnResolution);
    }
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

  public long getPromiseId() {
    return 0;
  }

  public final Actor getOwner() {
    return owner;
  }

  public static void setSOMClass(final SClass cls) {
    assert promiseClass == null || cls == null;
    promiseClass = cls;
  }

  public final synchronized SPromise getChainedPromiseFor(final Actor target) {
    SPromise remote = SPromise.createPromise(target, haltOnResolver,
        haltOnResolution, null);
    if (VmSettings.MEDEOR_TRACING) {
      MedeorTrace.promiseChained(getPromiseId(), remote.getPromiseId());
    }
    if (isCompleted()) {
      remote.value = value;
      remote.resolutionState = resolutionState;
      if (VmSettings.ACTOR_TRACING || VmSettings.REPLAY) {
        ((STracingPromise) remote).resolvingActor = ((STracingPromise) this).resolvingActor;
      }
    } else {
      addChainedPromise(remote);
    }
    return remote;
  }

  final void registerWhenResolvedUnsynced(final PromiseMessage msg) {
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

  final void registerOnErrorUnsynced(final PromiseMessage msg) {
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

  public static class STracingPromise extends SPromise {

    protected STracingPromise(final Actor owner, final boolean haltOnResolver,
        final boolean haltOnResolution) {
      super(owner, haltOnResolver, haltOnResolution);
    }

    protected int resolvingActor;

    public int getResolvingActor() {
      assert isCompleted();
      return resolvingActor;
    }

  }

  public static final class SMedeorPromise extends STracingPromise {
    protected final long promiseId;

    protected SMedeorPromise(final Actor owner, final boolean haltOnResolver,
        final boolean haltOnResolution, final SourceSection section) {
      super(owner, haltOnResolver, haltOnResolution);
      promiseId = TracingActivityThread.newEntityId();
      MedeorTrace.passiveEntityCreation(
          PassiveEntityType.PROMISE, promiseId, section);
    }

    @Override
    public long getPromiseId() {
      return promiseId;
    }
  }

  public static SResolver createResolver(final SPromise promise) {
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
        final ValueProfile whenResolvedProfile) {
      // TODO: we should change the implementation of chained promises to
      // always move all the handlers to the other promise, then we
      // don't need to worry about traversing the chain, which can
      // lead to a stack overflow.
      // TODO: restore 10000 as parameter in testAsyncDeeplyChainedResolution
      if (promise.chainedPromise != null) {
        Object wrapped = promise.chainedPromise.owner.wrapForUse(result, current, null);
        resolveAndTriggerListenersUnsynced(type, result, wrapped,
            promise.chainedPromise, current, actorPool,
            promise.chainedPromise.haltOnResolution, whenResolvedProfile);
        resolveMoreChainedPromisesUnsynced(type, promise, result, current,
            actorPool, haltOnResolution, whenResolvedProfile);
      }
    }

    /**
     * Resolve the other promises that has been chained to the first promise.
     */
    @TruffleBoundary
    private static void resolveMoreChainedPromisesUnsynced(final Resolution type,
        final SPromise promise, final Object result, final Actor current,
        final ForkJoinPool actorPool, final boolean haltOnResolution,
        final ValueProfile whenResolvedProfile) {
      if (promise.chainedPromiseExt != null) {

        for (SPromise p : promise.chainedPromiseExt) {
          Object wrapped = p.owner.wrapForUse(result, current, null);
          resolveAndTriggerListenersUnsynced(type, result, wrapped, p, current,
              actorPool, haltOnResolution, whenResolvedProfile);
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
        final ValueProfile whenResolvedProfile) {
      assert !(result instanceof SPromise);

      if (VmSettings.ACTOR_TRACING || VmSettings.REPLAY) {
        ((STracingPromise) p).resolvingActor =
            ((TracingActor) EventualMessage.getActorCurrentMessageIsExecutionOn()).getActorId();
      } else if (VmSettings.MEDEOR_TRACING) {
        if (type == Resolution.SUCCESSFUL && p.resolutionState != Resolution.CHAINED) {
          MedeorTrace.promiseResolution(p.getPromiseId(), result);
        } else if (type == Resolution.ERRONEOUS) {
          MedeorTrace.promiseError(p.getPromiseId(), result);
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
        }

        if (type == Resolution.SUCCESSFUL) {
          scheduleAllWhenResolvedUnsync(p, result, current, actorPool, haltOnResolution,
              whenResolvedProfile);
        } else {
          assert type == Resolution.ERRONEOUS;
          scheduleAllOnErrorUnsync(p, result, current, actorPool, haltOnResolution);
        }
        resolveChainedPromisesUnsync(type, p, result, current, actorPool, haltOnResolution,
            whenResolvedProfile);
      }
    }

    /**
     * Schedule all whenResolved callbacks for the promise.
     */
    protected static void scheduleAllWhenResolvedUnsync(final SPromise promise,
        final Object result, final Actor current, final ForkJoinPool actorPool,
        final boolean haltOnResolution, final ValueProfile whenResolvedProfile) {
      if (promise.whenResolved != null) {
        promise.scheduleCallbacksOnResolution(result,
            whenResolvedProfile.profile(promise.whenResolved), current, actorPool,
            haltOnResolution);
        scheduleExtensions(promise, promise.whenResolvedExt, result, current,
            actorPool, haltOnResolution);
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
        promise.scheduleCallbacksOnResolution(result, promise.onError, current,
            actorPool, haltOnResolution);
        scheduleExtensions(promise, promise.onErrorExt, result, current,
            actorPool, haltOnResolution);
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
