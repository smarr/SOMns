package som.interpreter.actors;

import java.util.ArrayList;

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


public final class SPromise extends SObjectWithoutFields {
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
  private ArrayList<Object>    whenResolved;
  private ArrayList<SResolver> whenResolvedResolvers;

  private ArrayList<SBlock>    onError;
  private ArrayList<SResolver> onErrorResolvers;

  private ArrayList<SClass>    onException;
  private ArrayList<SBlock>    onExceptionCallbacks;
  private ArrayList<SResolver> onExceptionResolvers;

  private ArrayList<SPromise>  chainedPromises;

  private Object  value;
  private boolean resolved;
  private boolean errored;
  private boolean chained;

  // the owner of this promise, on which all call backs are scheduled
  private final Actor owner;


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
    } else {
      assert chained;
      r += ", chained";
    }
    return r + ", " + value.toString() + "]";
  }

  @Override
  public boolean isValue() {
    return false;
  }

  public static void setSOMClass(final SClass cls) {
    assert promiseClass == null || cls == null;
    promiseClass = cls;
  }

  public SPromise whenResolved(final SBlock block) {
    assert block.getMethod().getNumberOfArguments() == 2;
    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = new SResolver(promise);

    registerWhenResolved(block, resolver);

    return promise;
  }

  public SPromise whenResolved(final SSymbol selector, final Object[] args) {
    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = new SResolver(promise);

    assert owner == EventualMessage.getActorCurrentMessageIsExecutionOn() : "think this should be true because the promise is an Object and owned by this specific actor";
    EventualMessage msg = new EventualMessage(owner, selector, args, resolver);
    registerWhenResolved(msg, resolver);
    return promise;
  }

  public SPromise onError(final SBlock block) {
    assert block.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = new SResolver(promise);

    registerOnError(block, resolver);
    return promise;
  }

  public SPromise whenResolvedOrError(final SBlock resolved, final SBlock error) {
    assert resolved.getMethod().getNumberOfArguments() == 2;
    assert error.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = new SResolver(promise);

    synchronized (this) {
      registerWhenResolved(resolved, resolver);
      registerOnError(error, resolver);
    }

    return promise;
  }

  private synchronized void registerWhenResolved(final Object callbackOrMsg,
      final SResolver resolver) {
    if (resolved) {
      scheduleCallback(value, callbackOrMsg, resolver);
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
      scheduleCallback(value, block, resolver);
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

  public SPromise onException(final SClass exceptionClass, final SBlock block) {
    assert block.getMethod().getNumberOfArguments() == 2;

    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = new SResolver(promise);

    synchronized (this) {
      if (errored) {
        if (value instanceof SAbstractObject) {
          if (((SAbstractObject) value).getSOMClass() == exceptionClass) {
            scheduleCallback(value, block, resolver);
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

  protected void scheduleCallback(final Object result,
      final Object callbackOrMsg, final SResolver resolver) {
    assert owner != null;
    EventualMessage msg;
    Actor target;
    if (callbackOrMsg instanceof SBlock) {
      SBlock callback = (SBlock) callbackOrMsg;
      msg = new EventualMessage(owner, SResolver.valueSelector,
          new Object[] {callback, result}, resolver);
      target = owner;
    } else {
      assert callbackOrMsg instanceof EventualMessage;
      msg = (EventualMessage) callbackOrMsg;

      Actor sendingActor = msg.getTarget();
      assert sendingActor != null;

      if (result instanceof SFarReference) {
        target = ((SFarReference) result).getActor();
      } else {
        target = EventualMessage.getActorCurrentMessageIsExecutionOn();
      }
      msg.setReceiverForEventualPromiseSend(result, target, sendingActor);
    }
    target.enqueueMessage(msg);
  }

  public synchronized void addChainedPromise(@NotNull final SPromise promise) {
    assert promise != null;
    if (chainedPromises == null) {
      chainedPromises = new ArrayList<>(1);
    }
    chainedPromises.add(promise);
  }

  public static final class SResolver extends SObjectWithoutFields {
    @CompilationFinal private static SClass resolverClass;

    private final SPromise promise;
    private static final SSymbol valueSelector = Symbols.symbolFor("value:");

    public SResolver(final SPromise promise) {
      super(resolverClass);
      this.promise = promise;
      assert resolverClass != null;
    }

    @Override
    public boolean isValue() {
      return true;
    }

    public static void setSOMClass(final SClass cls) {
      assert resolverClass == null || cls == null;
      resolverClass = cls;
    }

    public void onError() {
      throw new NotYetImplementedException(); // TODO: implement
    }

    public void resolve(Object result) {
      CompilerAsserts.neverPartOfCompilation("This has so many possible cases, we definitely want to optimize this");

      assert promise.value == null;
      assert !promise.resolved;
      assert !promise.errored;
      assert !promise.chained;

      if (result == promise) {
        // TODO: figure out whether this case is relevant
        return;  // this might happen at least in AmbientTalk, but doesn't do anything
      }

      // actors should have always direct access to their own objects and
      // thus, far references need to be unwrapped if they are returned back
      // to the owner
      // if a reference is delivered to another actor, it needs to be wrapped
      // in a far reference
      result = promise.owner.wrapForUse(result, EventualMessage.getActorCurrentMessageIsExecutionOn());

      if (result instanceof SPromise) {
        synchronized (promise) {
          promise.chained = true;
          ((SPromise) result).addChainedPromise(promise);
        }
        return;
      }

      assert !(result instanceof SPromise);

      synchronized (promise) {
        promise.value    = result;
        promise.resolved = true;

        scheduleAll(promise, result);
        resolveChainedPromises(promise, result);
      }
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
          scheduleAll(p, result);
          resolveChainedPromises(p, result);
        }
      }
    }

    protected static void scheduleAll(final SPromise promise, final Object result) {
      if (promise.whenResolved != null) {
        for (int i = 0; i < promise.whenResolved.size(); i++) {
          Object callbackOrMsg = promise.whenResolved.get(i);
          SResolver resolver = promise.whenResolvedResolvers.get(i);
          promise.scheduleCallback(result, callbackOrMsg, resolver);
        }
      }
    }
  }

  @CompilationFinal public static SClass pairClass;
  public static void setPairClass(final SClass cls) {
    assert pairClass == null || cls == null;
    pairClass = cls;
  }
}
