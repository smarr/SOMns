package som.interpreter.actors;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import som.VM;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.vm.NotYetImplementedException;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;
import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.sun.istack.internal.NotNull;


public class SPromise extends SObjectWithClass {
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
  protected boolean resolved;
  protected boolean errored;
  protected boolean chained;

  // the owner of this promise, on which all call backs are scheduled
  protected final Actor owner;

  public SPromise(@NotNull final Actor owner) {
    super(promiseClass);
    assert owner != null;
    this.owner = owner;

    resolved = false;
    assert promiseClass != null;
  }

  @Override
  public String toString() {
    String r = "Promise[" + owner.toString();
    if (resolved) {
      r += ", resolved";
    } else if (errored) {
      r += ", errored";
    } else if (chained) {
      assert chained;
      r += ", chained";
    } else {
      r += ", unresolved";
    }
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

  public final SPromise whenResolved(final SBlock block,
      final RootCallTarget blockCallTarget) {
    assert block.getMethod().getNumberOfArguments() == 2;
    SPromise  promise  = createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = createResolver(promise, "wR:block");

    PromiseCallbackMessage msg = new PromiseCallbackMessage(owner, block,
        resolver, blockCallTarget);
    registerWhenResolved(msg);

    return promise;
  }

  public final SPromise whenResolved(final SSymbol selector, final Object[] args, final RootCallTarget onResolve) {
    SPromise  promise  = createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = createResolver(promise, "eventualSendToPromise:", selector);

    assert owner == EventualMessage.getActorCurrentMessageIsExecutionOn() : "think this should be true because the promise is an Object and owned by this specific actor";
    PromiseSendMessage msg = new PromiseSendMessage(selector, args, owner, resolver, onResolve);
    registerWhenResolved(msg);
    return promise;
  }

  public final SPromise onError(final SBlock block, final RootCallTarget blockCallTarget) {
    assert block.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = createResolver(promise, "oE:block");

    PromiseCallbackMessage msg = new PromiseCallbackMessage(owner, block, resolver, blockCallTarget);
    registerOnError(msg);
    return promise;
  }

  public final SPromise whenResolvedOrError(final SBlock resolved,
      final SBlock error, final RootCallTarget resolverTarget,
      final RootCallTarget errorTarget) {
    assert resolved.getMethod().getNumberOfArguments() == 2;
    assert error.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = createResolver(promise, "wROE:block:block");

    PromiseCallbackMessage onResolved = new PromiseCallbackMessage(owner, resolved, resolver, resolverTarget);
    PromiseCallbackMessage onError    = new PromiseCallbackMessage(owner, error, resolver, errorTarget);

    synchronized (this) {
      registerWhenResolved(onResolved);
      registerOnError(onError);
    }

    return promise;
  }

  private synchronized void registerWhenResolved(final PromiseMessage callbackOrMsg) {
    if (resolved) {
      scheduleCallbacksOnResolution(value, callbackOrMsg);
    } else {
      if (errored) { // short cut, this promise will never be resolved
        return;
      }

      if (whenResolved == null) {
        whenResolved = new ArrayList<>(2);
      }
      whenResolved.add(callbackOrMsg);
    }
  }

  private synchronized void registerOnError(final PromiseMessage msg) {
    if (errored) {
      scheduleCallbacksOnResolution(value, msg);
    } else {
      if (resolved) { // short cut, this promise will never error, so, just return promise
        return;
      }
      if (onError == null) {
        onError          = new ArrayList<>(1);
      }
      onError.add(msg);
    }
  }

  public final SPromise onException(final SClass exceptionClass,
      final SBlock block, final RootCallTarget blockCallTarget) {
    assert block.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = createResolver(promise, "oEx:class:block");

    PromiseCallbackMessage msg = new PromiseCallbackMessage(owner, block, resolver, blockCallTarget);

    synchronized (this) {
      if (errored) {
        if (value instanceof SAbstractObject) {
          if (((SAbstractObject) value).getSOMClass() == exceptionClass) {
            scheduleCallbacksOnResolution(value, msg);
          }
        }
      } else {
        if (resolved) { // short cut, this promise will never error, so, just return promise
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
      final PromiseMessage msg) {
    // when a promise is resolved, we need to schedule all the
    // #whenResolved:/#onError:/... callbacks msgs as well as all eventual send
    // msgs to the promise

    assert owner != null;
    msg.resolve(result, owner, EventualMessage.getActorCurrentMessageIsExecutionOn());
    msg.getTarget().enqueueMessage(msg);
  }

  public final synchronized void addChainedPromise(@NotNull final SPromise promise) {
    assert promise != null;
    if (chainedPromises == null) {
      chainedPromises = new ArrayList<>(1);
    }
    chainedPromises.add(promise);
  }

  public final synchronized boolean isSomehowResolved() {
    return resolved || errored || chained;
  }

  public final synchronized boolean isResolved() {
    return resolved;
  }

  /** REM: this method needs to be used with self synchronized. */
  final void copyValueToRemotePromise(final SPromise remote) {
    remote.value    = value;
    remote.resolved = resolved;
    remote.errored  = errored;
    remote.chained  = chained;
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
      if (resolved) {
        r += ", resolved";
      } else if (errored) {
        r += ", errored";
      } else if (chained) {
        assert chained;
        r += ", chained";
      } else {
        r += ", unresolved";
      }
      return r + (value == null ? "" : ", " + value.toString()) + ", id:" + id + "]";
    }
  }

  public static SResolver createResolver(final SPromise promise,
      final String debugNote, final Object extraObj) {
    return createResolver(promise, debugNote + extraObj.toString());
  }

  public static SResolver createResolver(final SPromise promise, final String debugNote) {
    if (VM.DebugMode) {
      return new SResolver(promise);
    } else {
      return new SDebugResolver(promise, debugNote);
    }
  }

  public static class SResolver extends SObjectWithClass {
    @CompilationFinal private static SClass resolverClass;

    protected final SPromise promise;

    protected SResolver(final SPromise promise) {
      super(resolverClass);
      this.promise = promise;
      assert resolverClass != null;
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

    public final void resolve(final Object result) {
      VM.thisMethodNeedsToBeOptimized("This has so many possible cases, we definitely want to optimize this");

      assert !promise.isSomehowResolved() : "Not sure yet what to do with re-resolving of promises? just ignore it? Error?";
      assert promise.value == null        : "If it isn't resolved yet, it shouldn't have a value";

      if (result == promise) {
        // TODO: figure out whether this case is relevant
        return;  // this might happen at least in AmbientTalk, but doesn't do anything
      }

      if (result instanceof SPromise) {
        synchronized (promise) {
          promise.chained = true;
          ((SPromise) result).addChainedPromise(promise);
        }
        return;
      }

      resolveAndTriggerListeners(result, promise);
    }

    // TODO: solve the TODO and then remove the TruffleBoundary, this might even need to go into a node
    @TruffleBoundary
    protected static void resolveChainedPromises(final SPromise promise,
        final Object result) {
      // TODO: we should change the implementation of chained promises to
      //       always move all the handlers to the other promise, then we
      //       don't need to worry about traversing the chain, which can
      //       lead to a stack overflow.
      // TODO: restore 10000 as parameter in testAsyncDeeplyChainedResolution
      if (promise.chainedPromises != null) {
        for (SPromise p : promise.chainedPromises) {
          resolveAndTriggerListeners(result, p);
        }
      }
    }

    protected static void resolveAndTriggerListeners(final Object result,
        final SPromise p) {
      // actors should have always direct access to their own objects and
      // thus, far references need to be unwrapped if they are returned back
      // to the owner
      // if a reference is delivered to another actor, it needs to be wrapped
      // in a far reference
      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      Object wrapped = p.owner.wrapForUse(result, current);

      assert !(result instanceof SPromise);

      synchronized (p) {
        p.value = wrapped;
        p.resolved = true;
        scheduleAll(p, result);
        resolveChainedPromises(p, result);
      }
    }

    protected static void scheduleAll(final SPromise promise, final Object result) {
      if (promise.whenResolved != null) {
        for (int i = 0; i < promise.whenResolved.size(); i++) {
          PromiseMessage callbackOrMsg = promise.whenResolved.get(i);
          promise.scheduleCallbacksOnResolution(result, callbackOrMsg);
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
