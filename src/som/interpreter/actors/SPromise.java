package som.interpreter.actors;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import som.VM;
import som.vm.NotYetImplementedException;
import som.vm.Symbols;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithoutFields;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.sun.istack.internal.NotNull;


public class SPromise extends SObjectWithoutFields {
  @CompilationFinal private static SClass promiseClass;

  // THREAD-SAFETY: these fields are subject to race conditions and should only
  //                be accessed when under the SPromise(this) lock
  //                currently, we minimize locking by first setting the result
  //                value and resolved flag, and then release the lock again
  //                this makes sure that the promise owner directly schedules
  //                call backs, and does not block the resolver to schedule
  //                call backs here either. After resolving the future,
  //                whenResolved and whenBroken should only be accessed by the
  //                resolver actor
  protected ArrayList<Object>    whenResolved;
  protected ArrayList<SResolver> whenResolvedResolvers;

  protected ArrayList<SBlock>    onError;
  protected ArrayList<SResolver> onErrorResolvers;

  protected ArrayList<SClass>    onException;
  protected ArrayList<SBlock>    onExceptionCallbacks;
  protected ArrayList<SResolver> onExceptionResolvers;

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

  public final SPromise whenResolved(final SBlock block) {
    assert block.getMethod().getNumberOfArguments() == 2;
    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = createResolver(promise, "wR:block");

    registerWhenResolved(block, resolver);

    return promise;
  }

  public final SPromise whenResolved(final SSymbol selector, final Object[] args) {
    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = createResolver(promise, "eventualSendToPromise:", selector);

    assert owner == EventualMessage.getActorCurrentMessageIsExecutionOn() : "think this should be true because the promise is an Object and owned by this specific actor";
    EventualMessage msg = new EventualMessage(owner, selector, args, resolver, null);
    registerWhenResolved(msg, resolver);
    return promise;
  }

  public final SPromise onError(final SBlock block) {
    assert block.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = createResolver(promise, "oE:block");

    registerOnError(block, resolver);
    return promise;
  }

  public final SPromise whenResolvedOrError(final SBlock resolved, final SBlock error) {
    assert resolved.getMethod().getNumberOfArguments() == 2;
    assert error.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = createResolver(promise, "wROE:block:block");

    synchronized (this) {
      registerWhenResolved(resolved, resolver);
      registerOnError(error, resolver);
    }

    return promise;
  }

  private synchronized void registerWhenResolved(final Object callbackOrMsg,
      final SResolver resolver) {
    if (resolved) {
      scheduleCallbacksOnResolution(value, callbackOrMsg, resolver);
    } else {
      if (errored) { // short cut, this promise will never be resolved
        return;
      }

      if (whenResolved == null) {
        whenResolved          = new ArrayList<>(2);
        whenResolvedResolvers = new ArrayList<>(2);
      }
      whenResolved.add(callbackOrMsg);
      whenResolvedResolvers.add(resolver);
    }
  }

  private synchronized void registerOnError(final SBlock block,
      final SResolver resolver) {
    if (errored) {
      scheduleCallbacksOnResolution(value, block, resolver);
    } else {
      if (resolved) { // short cut, this promise will never error, so, just return promise
        return;
      }
      if (onError == null) {
        onError          = new ArrayList<>(1);
        onErrorResolvers = new ArrayList<>(1);
      }
      onError.add(block);
      onErrorResolvers.add(resolver);
    }
  }

  public final SPromise onException(final SClass exceptionClass, final SBlock block) {
    assert block.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = createResolver(promise, "oEx:class:block");

    synchronized (this) {
      if (errored) {
        if (value instanceof SAbstractObject) {
          if (((SAbstractObject) value).getSOMClass() == exceptionClass) {
            scheduleCallbacksOnResolution(value, block, resolver);
          }
        }
      } else {
        if (resolved) { // short cut, this promise will never error, so, just return promise
          return promise;
        }
        if (onException == null) {
          onException          = new ArrayList<>(1);
          onExceptionResolvers = new ArrayList<>(1);
          onExceptionCallbacks = new ArrayList<>(1);
        }
        onException.add(exceptionClass);
        onExceptionCallbacks.add(block);
        onExceptionResolvers.add(resolver);
      }
    }
    return promise;
  }

  protected final void scheduleCallbacksOnResolution(final Object result,
      final Object callbackOrMsg, final SResolver resolver) {
    // when a promise is resolved, we need to schedule all the
    // #whenResolved:/#onError:/... callbacks as well as all eventual sends
    // to the promise

    assert owner != null;
    EventualMessage msg;
    Actor target;
    if (callbackOrMsg instanceof SBlock) {
      // schedule the #whenResolved:/#onError:/... callback
      SBlock callback = (SBlock) callbackOrMsg;
      msg = new EventualMessage(owner, SResolver.valueSelector,
          new Object[] {callback, result}, resolver, EventualMessage.getActorCurrentMessageIsExecutionOn());
      target = owner;
    } else {
      // schedule the eventual message to the promise value
      assert callbackOrMsg instanceof EventualMessage;
      msg = (EventualMessage) callbackOrMsg;

      if (result instanceof SFarReference) {
        // for far references, we need to schedule the message on the owner of the referenced value
        target = ((SFarReference) result).getActor();
      } else {
        // otherwise, we schedule it on the promise's owner
        target = owner;
      }
      msg.setReceiverForEventualPromiseSend(result, target, EventualMessage.getActorCurrentMessageIsExecutionOn());
    }
    target.enqueueMessage(msg);
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

  public static final class SDebugPromise extends SPromise {
    private static final AtomicInteger idGenerator = new AtomicInteger(0);

    private final int id;

    public SDebugPromise(final Actor owner) {
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

  public static class SResolver extends SObjectWithoutFields {
    @CompilationFinal private static SClass resolverClass;
    private static final SSymbol valueSelector = Symbols.symbolFor("value:");

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
      CompilerAsserts.neverPartOfCompilation("This has so many possible cases, we definitely want to optimize this");

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
      Object wrapped = p.owner.wrapForUse(result, EventualMessage.getActorCurrentMessageIsExecutionOn());

      assert !(result instanceof SPromise);

      synchronized (p) {
        p.value = wrapped;
        p.resolved = true;
        scheduleAll(p, wrapped);
        resolveChainedPromises(p, wrapped);
      }
    }

    protected static void scheduleAll(final SPromise promise, final Object result) {
      if (promise.whenResolved != null) {
        for (int i = 0; i < promise.whenResolved.size(); i++) {
          Object callbackOrMsg = promise.whenResolved.get(i);
          SResolver resolver = promise.whenResolvedResolvers.get(i);
          promise.scheduleCallbacksOnResolution(result, callbackOrMsg, resolver);
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
