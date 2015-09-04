package som.interpreter.actors;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import som.VM;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.vm.NotYetImplementedException;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.sun.istack.internal.NotNull;


public class SPromise extends SObjectWithClass {
  private enum Resolution {
    UNRESOLVED, ERRORNOUS, SUCCESSFUL, CHAINED
  }

  @CompilationFinal private static SClass promiseClass;

  public static SPromise createPromise(final Actor owner) {
    if (VM.DebugMode) {
      return new SDebugPromise(owner);
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
  protected ArrayList<PromiseMessage> whenResolved;
  protected ArrayList<PromiseMessage> onError;

  protected ArrayList<SClass>         onException;
  protected ArrayList<PromiseMessage> onExceptionCallbacks;

  protected ArrayList<SPromise>  chainedPromises;

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

  public final Actor getOwner() {
    return owner;
  }

  public static void setSOMClass(final SClass cls) {
    assert promiseClass == null || cls == null;
    promiseClass = cls;
  }

  public final SPromise onError(final SBlock block, final Actor current) {
    assert block.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = createPromise(current);
    SResolver resolver = createResolver(promise, "oE:block");

    PromiseCallbackMessage msg = new PromiseCallbackMessage(owner, block, resolver);
    registerOnError(msg, current);
    return promise;
  }

  @TruffleBoundary
  final void registerWhenResolvedUnsynced(final PromiseMessage msg) {
    if (whenResolved == null) {
      whenResolved = new ArrayList<>(1);
    }
    whenResolved.add(msg);
  }

  public synchronized void registerOnError(final PromiseMessage msg,
      final Actor current) {
    if (resolutionState == Resolution.ERRORNOUS) {
      scheduleCallbacksOnResolution(value, msg, current);
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
      final SBlock block, final Actor current) {
    assert block.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = createPromise(current);
    SResolver resolver = createResolver(promise, "oEx:class:block");

    PromiseCallbackMessage msg = new PromiseCallbackMessage(owner, block, resolver);

    synchronized (this) {
      if (resolutionState == Resolution.ERRORNOUS) {
        if (value instanceof SAbstractObject) {
          if (((SAbstractObject) value).getSOMClass() == exceptionClass) {
            scheduleCallbacksOnResolution(value, msg, current);
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
    }
    return promise;
  }

  protected final void scheduleCallbacksOnResolution(final Object result,
      final PromiseMessage msg, final Actor current) {
    // when a promise is resolved, we need to schedule all the
    // #whenResolved:/#onError:/... callbacks msgs as well as all eventual send
    // msgs to the promise

    assert owner != null;
    msg.resolve(result, owner, current);
    msg.getTarget().enqueueMessage(msg);
  }

  @TruffleBoundary
  public final synchronized void addChainedPromise(@NotNull final SPromise promise) {
    assert promise != null;
    promise.resolutionState = Resolution.CHAINED;

    if (chainedPromises == null) {
      chainedPromises = new ArrayList<>(1);
    }
    chainedPromises.add(promise);
  }

  public final synchronized boolean isSomehowResolved() {
    return resolutionState != Resolution.UNRESOLVED;
  }

  /** Internal Helper, only to be used properly synchronized. */
  final boolean isResolvedUnsync() {
    return resolutionState == Resolution.SUCCESSFUL;
  }

  /** Internal Helper, only to be used properly synchronized. */
  final boolean isErroredUnsync() {
    return resolutionState == Resolution.ERRORNOUS;
  }

  /** Internal Helper, only to be used properly synchronized. */
  final Object getValueUnsync() {
    return value;
  }

  /** REM: this method needs to be used with self synchronized. */
  final void copyValueToRemotePromise(final SPromise remote) {
    remote.value = value;
    remote.resolutionState = resolutionState;
  }

  protected static final class SDebugPromise extends SPromise {
    private static final AtomicInteger idGenerator = new AtomicInteger(0);

    private final int id;

    protected SDebugPromise(final Actor owner) {
      super(owner);
      id = idGenerator.getAndIncrement();
    }

    @Override
    public String toString() {
      String r = "Promise[" + owner.toString();
      r += ", " + resolutionState.name();
      return r + (value == null ? "" : ", " + value.toString()) + ", id:" + id + "]";
    }
  }

  public static SResolver createResolver(final SPromise promise,
      final String debugNote, final Object extraObj) {
    return createResolver(promise, debugNote + extraObj.toString());
  }

  public static SResolver createResolver(final SPromise promise, final String debugNote) {
    if (VM.DebugMode) {
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

    public final void onError() {
      throw new NotYetImplementedException(); // TODO: implement
    }

    public final boolean assertNotResolved() {
      assert !promise.isSomehowResolved() : "Not sure yet what to do with re-resolving of promises? just ignore it? Error?";
      assert promise.value == null        : "If it isn't resolved yet, it shouldn't have a value";
      return true;
    }

    // TODO: solve the TODO and then remove the TruffleBoundary, this might even need to go into a node
    @TruffleBoundary
    protected static void resolveChainedPromises(final SPromise promise,
        final Object result, final Actor current) {
      // TODO: we should change the implementation of chained promises to
      //       always move all the handlers to the other promise, then we
      //       don't need to worry about traversing the chain, which can
      //       lead to a stack overflow.
      // TODO: restore 10000 as parameter in testAsyncDeeplyChainedResolution
      if (promise.chainedPromises != null) {
        for (SPromise p : promise.chainedPromises) {
          Object wrapped = p.owner.wrapForUse(result, current);
          resolveAndTriggerListeners(result, wrapped, p, current);
        }
      }
    }

    protected static void resolveAndTriggerListeners(final Object result,
        final Object wrapped, final SPromise p, final Actor current) {
      assert !(result instanceof SPromise);

      synchronized (p) {
        p.value = wrapped;
        p.resolutionState = Resolution.SUCCESSFUL;
        scheduleAll(p, result, current);
        resolveChainedPromises(p, result, current);
      }
    }

    protected static void scheduleAll(final SPromise promise,
        final Object result, final Actor current) {
      if (promise.whenResolved != null) {
        for (int i = 0; i < promise.whenResolved.size(); i++) {
          PromiseMessage callbackOrMsg = promise.whenResolved.get(i);
          promise.scheduleCallbacksOnResolution(result, callbackOrMsg, current);
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
}
