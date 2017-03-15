package som.interpreter.actors;

import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.sun.istack.internal.NotNull;

import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.vm.VmSettings;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.ReplayActor;


public class SPromise extends SObjectWithClass {
  public enum Resolution {
    UNRESOLVED, ERRONEOUS, SUCCESSFUL, CHAINED
  }

  @CompilationFinal private static SClass promiseClass;

  private boolean triggerResolutionBreakpointOnUnresolvedChainedPromise;

  public static SPromise createPromise(final Actor owner) {
    if (VmSettings.REPLAY) {
      return new SReplayPromise(owner);
    } else if (VmSettings.PROMISE_CREATION) {
      return new STracingPromise(owner);
    } else {
      return new SPromise(owner);
    }
  }


  // THREAD-SAFETY: these fields are subject to race conditions and should only
  //                be accessed when under the SPromise(this) lock
  //                currently, we minimize locking by first setting the result
  //                value and resolved flag, and then release the lock again
  //                this makes sure that the promise owner directly schedules
  //                call backs, and does not block the resolver to schedule
  //                call backs here either. After resolving the future,
  //                whenResolved and whenBroken should only be accessed by the
  //                resolver actor
  protected PromiseMessage whenResolved;
  protected ArrayList<PromiseMessage> whenResolvedExt;
  protected PromiseMessage onError;
  protected ArrayList<PromiseMessage> onErrorExt;

  protected SPromise chainedPromise;
  protected ArrayList<SPromise>  chainedPromiseExt;

  protected Object  value;
  protected Resolution resolutionState;

  // the owner of this promise, on which all call backs are scheduled
  protected final Actor owner;

  protected SPromise(@NotNull final Actor owner) {
    super(promiseClass, promiseClass.getInstanceFactory());
    assert owner != null;
    this.owner = owner;

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

  public long getPromiseId() { return 0; }
  public long getReplayPromiseId() { return 0; }

  public final Actor getOwner() {
    return owner;
  }

  public static void setSOMClass(final SClass cls) {
    assert promiseClass == null || cls == null;
    promiseClass = cls;
  }

  public final synchronized SPromise getChainedPromiseFor(final Actor target) {
    SPromise remote = SPromise.createPromise(target);
    if (isCompleted()) {
      remote.value = value;
      remote.resolutionState = resolutionState;
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
      final PromiseMessage msg, final Actor current, final boolean isBreakpointOnPromiseResolution) {
    // when a promise is resolved, we need to schedule all the
    // #whenResolved:/#onError:/... callbacks msgs as well as all eventual send
    // msgs to the promise

    assert owner != null;
    msg.resolve(result, owner, current);

    // update the message flag for the message breakpoint at receiver side
    msg.setIsMessageReceiverBreakpoint(isBreakpointOnPromiseResolution);
    msg.getTarget().send(msg);
  }

  public final synchronized void addChainedPromise(@NotNull final SPromise remote) {
    assert remote != null;
    remote.resolutionState = Resolution.CHAINED;
    if (VmSettings.PROMISE_RESOLUTION) {
      ActorExecutionTrace.promiseChained(this.getPromiseId(), remote.getPromiseId());
    }

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
    assert value == null  : "If it isn't resolved yet, it shouldn't have a value";
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

  protected static class STracingPromise extends SPromise {
    protected final long promiseId;

    protected STracingPromise(final Actor owner) {
      super(owner);
      TracingActivityThread t = (TracingActivityThread) Thread.currentThread();
      promiseId = t.generatePromiseId();
      ActorExecutionTrace.promiseCreation(promiseId);
    }

    @Override
    public long getPromiseId() {
      return promiseId;
    }
  }

  protected static final class SReplayPromise extends STracingPromise {
    protected final long replayId;

    protected SReplayPromise(final Actor owner) {
      super(owner);
      ReplayActor creator = (ReplayActor) EventualMessage.getActorCurrentMessageIsExecutionOn();

      assert creator.getReplayPromiseIds() != null && creator.getReplayPromiseIds().size() > 0;
      replayId = creator.getReplayPromiseIds().remove();
    }

    @Override
    public long getReplayPromiseId() { return replayId; }
  }

  public static SResolver createResolver(final SPromise promise,
      final String debugNote, final Object extraObj) {
    return createResolver(promise, debugNote + extraObj.toString());
  }

  public static SResolver createResolver(final SPromise promise, final String debugNote) {
    if (VmSettings.DEBUG_MODE) {
      return new SDebugResolver(promise, debugNote);
    } else {
      return new SResolver(promise);
    }
  }

  public static class SResolver extends SObjectWithClass {
    @CompilationFinal private static SClass resolverClass;

    protected final SPromise promise;

    protected SResolver(final SPromise promise) {
      super(resolverClass, resolverClass.getInstanceFactory());
      this.promise = promise;
      assert resolverClass != null;
    }

    public final SPromise getPromise() {
      return promise;
    }

    @Override
    public final boolean isValue() {
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

    public final boolean assertNotCompleted() {
      return promise.assertNotCompleted();
    }

    /**
     * Handles the case when a promise is resolved with a proper value
     * and previously has been chained with other promises.
     */
    // TODO: solve the TODO and then remove the TruffleBoundary, this might even need to go into a node
    @TruffleBoundary
    protected static void resolveChainedPromisesUnsync(final Resolution type,
        final SPromise promise, final Object result, final Actor current,
        final boolean isBreakpointOnPromiseResolution) {
      // TODO: we should change the implementation of chained promises to
      //       always move all the handlers to the other promise, then we
      //       don't need to worry about traversing the chain, which can
      //       lead to a stack overflow.
      // TODO: restore 10000 as parameter in testAsyncDeeplyChainedResolution
      if (promise.chainedPromise != null) {
        Object wrapped = promise.chainedPromise.owner.wrapForUse(result, current, null);
        resolveAndTriggerListenersUnsynced(type, result, wrapped,
            promise.chainedPromise, current,
            promise.chainedPromise.isTriggerResolutionBreakpointOnUnresolvedChainedPromise());
        resolveMoreChainedPromisesUnsynced(type, promise, result, current,
            isBreakpointOnPromiseResolution);
      }
    }
    /**
     * Resolve the other promises that has been chained to the first promise.
     */
    @TruffleBoundary
    private static void resolveMoreChainedPromisesUnsynced(final Resolution type,
        final SPromise promise, final Object result, final Actor current,
        final boolean isBreakpointOnPromiseResolution) {
      if (promise.chainedPromiseExt != null) {

        for (SPromise p : promise.chainedPromiseExt) {
          Object wrapped = p.owner.wrapForUse(result, current, null);
          resolveAndTriggerListenersUnsynced(type, result, wrapped, p, current,
              isBreakpointOnPromiseResolution);
        }
      }
    }

    /**
     * Resolution of a promise with a proper value.
     * All callbacks for this promise are going to be scheduled.
     * If the promise was chained with other promises, the chained promises are also resolved.
     */
    protected static void resolveAndTriggerListenersUnsynced(final Resolution type,
        final Object result, final Object wrapped, final SPromise p,
        final Actor current, final boolean isBreakpointOnPromiseResolution) {
      assert !(result instanceof SPromise);

      if (VmSettings.PROMISE_RESOLUTION) {
        if (p.resolutionState == Resolution.SUCCESSFUL) {
          ActorExecutionTrace.promiseResolution(p.getPromiseId(), result);
        } else if (p.resolutionState == Resolution.ERRONEOUS) {
          ActorExecutionTrace.promiseError(p.getPromiseId(), result);
        }
      }

      // LOCKING NOTE: we need a synchronization unit that is not the promise,
      //               because otherwise we might end up in a deadlock, but we still need to group the
      //               scheduling of messages and the propagation of the resolution state, otherwise
      //               we might see message ordering issues
      synchronized (wrapped) {
        // LOCKING NOTE: We can split the scheduling out of the synchronized
        // because after resolving the promise, all clients will schedule their
        // callbacks/msg themselves
        synchronized (p) {
          assert p.assertNotCompleted();
          // TODO: is this correct? can we just resolve chained promises like this? this means, their state changes twice. I guess it is ok, not sure about synchronization thought. They are created as 'chained', and then there is the resolute propagation accross chained promisses
          // TODO use a special constructor to create chained promises???
          p.value = wrapped;
          p.resolutionState = type;
        }

        if (type == Resolution.SUCCESSFUL) {
          scheduleAllWhenResolvedUnsync(p, result, current, isBreakpointOnPromiseResolution);
        } else {
          assert type == Resolution.ERRONEOUS;
          scheduleAllOnErrorUnsync(p, result, current, isBreakpointOnPromiseResolution);
        }
        resolveChainedPromisesUnsync(type, p, result, current, isBreakpointOnPromiseResolution);
      }
    }

    /**
     * Schedule all whenResolved callbacks for the promise.
     */
    protected static void scheduleAllWhenResolvedUnsync(final SPromise promise,
        final Object result, final Actor current, final boolean isBreakpointOnPromiseResolution) {
      if (promise.whenResolved != null) {
        promise.scheduleCallbacksOnResolution(result, promise.whenResolved, current, isBreakpointOnPromiseResolution);
        scheduleExtensions(promise, promise.whenResolvedExt, result, current,
            isBreakpointOnPromiseResolution);
      }
    }

    /**
     *  Schedule callbacks from the whenResolvedExt extension array.
     */
    @TruffleBoundary
    private static void scheduleExtensions(final SPromise promise,
        final ArrayList<PromiseMessage> extension,
        final Object result, final Actor current, final boolean isBreakpointOnPromiseResolution) {
      if (extension != null) {
        for (int i = 0; i < extension.size(); i++) {
          PromiseMessage callbackOrMsg = extension.get(i);
          promise.scheduleCallbacksOnResolution(result, callbackOrMsg, current, isBreakpointOnPromiseResolution);
        }
      }
    }

    /**
     * Schedule all onError callbacks for the promise.
     */
    protected static void scheduleAllOnErrorUnsync(final SPromise promise,
        final Object result, final Actor current, final boolean isBreakpointOnPromiseResolution) {
      if (promise.onError != null) {
        promise.scheduleCallbacksOnResolution(result, promise.onError, current, isBreakpointOnPromiseResolution);
        scheduleExtensions(promise, promise.onErrorExt, result, current, isBreakpointOnPromiseResolution);
      }
    }
  }

  public static final class SDebugResolver extends SResolver {
    private final String debugNote;

    private SDebugResolver(final SPromise promise, final String debugNote) {
      super(promise);
      this.debugNote = debugNote;
    }

    @Override
    public String toString() {
      return "Resolver[" + debugNote + "|" + promise.toString() + "]";
    }
  }

  @CompilationFinal public static SClass pairClass;
  public static void setPairClass(final SClass cls) {
    assert pairClass == null || cls == null;
    pairClass = cls;
  }

  boolean isTriggerResolutionBreakpointOnUnresolvedChainedPromise() {
    return triggerResolutionBreakpointOnUnresolvedChainedPromise;
  }

  void setTriggerResolutionBreakpointOnUnresolvedChainedPromise(
      final boolean triggerResolutionBreakpointOnUnresolvedChainedPromise) {
    this.triggerResolutionBreakpointOnUnresolvedChainedPromise = triggerResolutionBreakpointOnUnresolvedChainedPromise;
  }
}
