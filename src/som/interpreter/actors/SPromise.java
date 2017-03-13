package som.interpreter.actors;

import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.sun.istack.internal.NotNull;

import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.vm.NotYetImplementedException;
import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.TracingActivityThread;
import tools.concurrency.TracingActors.ReplayActor;


public class SPromise extends SObjectWithClass {
  private enum Resolution {
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
  protected ArrayList<PromiseMessage> onError;

  protected ArrayList<SClass>         onException;
  protected ArrayList<PromiseMessage> onExceptionCallbacks;

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

  public final SPromise onError(final SBlock block,
      final RootCallTarget blockCallTarget, final Actor current) {
    assert block.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = createPromise(current);
    SResolver resolver = createResolver(promise, "oE:block");

    PromiseCallbackMessage msg = new PromiseCallbackMessage(EventualMessage.getCurrentExecutingMessageId(),
        owner, block, resolver, blockCallTarget, false, false, false, promise);
    registerOnError(msg, current);
    return promise;
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

  public synchronized void registerOnError(final PromiseMessage msg,
      final Actor current) {
    if (resolutionState == Resolution.ERRONEOUS) {
        scheduleCallbacksOnResolution(value, msg, current, false);
    } else {
      if (resolutionState == Resolution.SUCCESSFUL) {
        // short cut on resolved, this promise will never error, so,
        // just return promise, don't use isSomehowResolved(), because the other
        // case are not correct
        return;
      }
      if (onError == null) {
        onError = new ArrayList<>(1);
      }
      onError.add(msg);
    }
  }

  public final SPromise onException(final SClass exceptionClass,
      final SBlock block, final RootCallTarget blockCallTarget, final Actor current) {
    assert block.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = createPromise(current);
    SResolver resolver = createResolver(promise, "oEx:class:block");

    PromiseCallbackMessage msg = new PromiseCallbackMessage(EventualMessage.getCurrentExecutingMessageId(), owner,
        block, resolver, blockCallTarget, false, false, false, promise);

    synchronized (this) {
      if (resolutionState == Resolution.ERRONEOUS) {
        if (value instanceof SAbstractObject) {
          if (((SAbstractObject) value).getSOMClass() == exceptionClass) {
            scheduleCallbacksOnResolution(value, msg, current, false);
          }
        }
      } else {
        if (resolutionState == Resolution.SUCCESSFUL) { // short cut, this promise will never error, so, just return promise
          return promise;
        }
        if (onException == null) {
          onException          = new ArrayList<>(1);
          onExceptionCallbacks = new ArrayList<>(1);
        }
        onException.add(exceptionClass);
        onExceptionCallbacks.add(msg);
      }
      return promise;
    }
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

    public static void onError(final SAbstractObject exception, final Object wrapped, final SPromise p, final Actor current) {
      // for trace  TODO fix tracing
      if (VmSettings.PROMISE_RESOLUTION) {
        ActorExecutionTrace.promiseRuin(p.getPromiseId(), exception);
      }
      synchronized (wrapped) {
        handleOrPasstoChain(exception, wrapped, p, current);
      }
    }

    public static void handleOrPasstoChain(final SAbstractObject exception, final Object wrapped, final SPromise p, final Actor current) {
      if (!runHandlersUnsync(exception, wrapped, p, current)) {
          // if I did not handle the exception ask chain to handle
          ruinChainedPromisesUnsynch(exception, p, current);
      }
    }

    private static boolean runHandlersUnsync(final SAbstractObject exception, final Object wrapped, final SPromise p, final Actor current) {
        synchronized (p) {
          assert p.assertNotCompleted();
          p.value = exception;
          p.resolutionState = Resolution.ERRONEOUS;
        }

        boolean handled = false;

        // execute all exceptionHandlers if the type of wrapped is equal to the exceptionClass the handler handles.
        if (p.onException != null) {
          for (int i = 0; i < p.onException.size(); i++) {
            SClass exceptionClass = p.onException.get(i);
            PromiseMessage handle = p.onExceptionCallbacks.get(i);
            if (((SAbstractObject) wrapped).getSOMClass() == exceptionClass) {
                handled = true;
                p.scheduleCallbacksOnResolution(exception, handle, current, false);
            }
          }
        }

        // execute all errorHandlers
        if (p.onError != null) {
          for (int i = 0; i < p.onError.size(); i++) {
            handled = true;
            PromiseMessage handle = p.onError.get(i);
            p.scheduleCallbacksOnResolution(exception, handle, current, false);
          }
        }
        return handled;
    }

    protected static void ruinChainedPromisesUnsynch(final SAbstractObject exception, final SPromise promise, final Actor current) {
      if (promise.chainedPromise != null) {
        Object wrapped = promise.chainedPromise.owner.wrapForUse(exception, current, null);
        handleOrPasstoChain(exception, wrapped, promise.chainedPromise, current);

        if (promise.chainedPromiseExt != null) {
          // multiple promises are chained to me, ruin all of them
          for (SPromise p : promise.chainedPromiseExt) {
            Object wrappedForP = p.owner.wrapForUse(exception, current, null);
            handleOrPasstoChain(exception, wrappedForP, p, current);
          }
        }
      }
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
    protected static void resolveChainedPromisesUnsync(final SPromise promise,
        final Object result, final Actor current, final boolean isBreakpointOnPromiseResolution) {
      // TODO: we should change the implementation of chained promises to
      //       always move all the handlers to the other promise, then we
      //       don't need to worry about traversing the chain, which can
      //       lead to a stack overflow.
      // TODO: restore 10000 as parameter in testAsyncDeeplyChainedResolution
      if (promise.chainedPromise != null) {
        Object wrapped = promise.chainedPromise.owner.wrapForUse(result, current, null);
        resolveAndTriggerListenersUnsynced(result, wrapped, promise.chainedPromise, current, promise.chainedPromise.isTriggerResolutionBreakpointOnUnresolvedChainedPromise());
        resolveMoreChainedPromisesUnsynced(promise, result, current, isBreakpointOnPromiseResolution);
      }
    }
    /**
     * Resolve the other promises that has been chained to the first promise.
     */
    @TruffleBoundary
    private static void resolveMoreChainedPromisesUnsynced(final SPromise promise,
        final Object result, final Actor current, final boolean isBreakpointOnPromiseResolution) {
      if (promise.chainedPromiseExt != null) {

        for (SPromise p : promise.chainedPromiseExt) {
          Object wrapped = p.owner.wrapForUse(result, current, null);
          resolveAndTriggerListenersUnsynced(result, wrapped, p, current, isBreakpointOnPromiseResolution);
        }
      }
    }

    /**
     * Resolution of a promise with a proper value.
     * All callbacks for this promise are going to be scheduled.
     * If the promise was chained with other promises, the chained promises are also resolved.
     */
    protected static void resolveAndTriggerListenersUnsynced(final Object result,
        final Object wrapped, final SPromise p, final Actor current, final boolean isBreakpointOnPromiseResolution) {
      assert !(result instanceof SPromise);

      if (VmSettings.PROMISE_RESOLUTION) {
        if (p.resolutionState != Resolution.CHAINED) {
          ActorExecutionTrace.promiseResolution(p.getPromiseId(), result);
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
          p.resolutionState = Resolution.SUCCESSFUL;
        }

        scheduleAllUnsync(p, result, current, isBreakpointOnPromiseResolution);
        resolveChainedPromisesUnsync(p, result, current, isBreakpointOnPromiseResolution);
      }
    }

    /**
     * Schedule all the callbacks for the promise.
     */
    protected static void scheduleAllUnsync(final SPromise promise,
        final Object result, final Actor current, final boolean isBreakpointOnPromiseResolution) {
      if (promise.whenResolved != null) {
        promise.scheduleCallbacksOnResolution(result, promise.whenResolved, current, isBreakpointOnPromiseResolution);
        scheduleExtensions(promise, result, current, isBreakpointOnPromiseResolution);
      }
    }

    /**
     *  Schedule callbacks from the whenResolvedExt extension array.
     */
    @TruffleBoundary
    private static void scheduleExtensions(final SPromise promise,
        final Object result, final Actor current, final boolean isBreakpointOnPromiseResolution) {
      if (promise.whenResolvedExt != null) {
        for (int i = 0; i < promise.whenResolvedExt.size(); i++) {
          PromiseMessage callbackOrMsg = promise.whenResolvedExt.get(i);
          promise.scheduleCallbacksOnResolution(result, callbackOrMsg, current, isBreakpointOnPromiseResolution);
        }
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
