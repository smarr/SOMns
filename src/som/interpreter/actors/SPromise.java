package som.interpreter.actors;

import java.util.ArrayList;

import som.vm.NotYetImplementedException;
import som.vm.Symbols;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithoutFields;
import som.vmobjects.SSymbol;

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
  private ArrayList<SBlock>    whenResolved;
  private ArrayList<SResolver> whenResolvedResolvers;

  private ArrayList<SBlock>    onError;
  private ArrayList<SResolver> onErrorResolvers;

  private ArrayList<SClass>    onException;
  private ArrayList<SBlock>    onExceptionCallbacks;
  private ArrayList<SResolver> onExceptionResolvers;

  private Object  value;
  private boolean resolved;
  private boolean errored;

  // the owner of this promise, on which all call backs are scheduled
  private final Actor owner;


  public SPromise(@NotNull final Actor owner) {
    super(promiseClass);
    assert owner != null;
    this.owner = owner;

    resolved     = false;
    assert promiseClass != null;
  }

  public static void setSOMClass(final SClass cls) {
    assert promiseClass == null;
    promiseClass = cls;
  }

  public SPromise whenResolved(final SBlock block) {
    assert block.getMethod().getNumberOfArguments() == 2;
    SPromise  promise  = new SPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
    SResolver resolver = new SResolver(promise);

    registerWhenResolved(block, resolver);

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

    registerWhenResolved(resolved, resolver);
    registerOnError(error, resolver);

    return promise;
  }

  private synchronized void registerWhenResolved(final SBlock block,
      final SResolver resolver) {
    if (resolved) {
      scheduleCallback(value, block, resolver);
    } else {
      if (errored) { // short cut, this promise will never be resolved
        return;
      }

      if (whenResolved == null) {
        whenResolved          = new ArrayList<>(2);
        whenResolvedResolvers = new ArrayList<>(2);
      }
      whenResolved.add(block);
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

  protected void scheduleCallback(final Object result, final SBlock callback, final SResolver resolver) {
    assert owner != null;
    EventualMessage msg = new EventualMessage(owner, SResolver.valueSelector,
        new Object[] {callback, result}, resolver);
    owner.enqueueMessage(msg);
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

    public static void setSOMClass(final SClass cls) {
      assert resolverClass == null;
      resolverClass = cls;
    }

    public void onError() {
      throw new NotYetImplementedException(); // TODO: implement
    }

    public void resolve(final Object result) {
      assert promise.value == null;
      assert !promise.resolved;
      assert !promise.errored;

      synchronized (promise) {
        promise.value    = result;
        promise.resolved = true;
      }

      if (promise.whenResolved != null) {
        for (int i = 0; i < promise.whenResolved.size(); i++) {
          SBlock    callback = promise.whenResolved.get(i);
          SResolver resolver = promise.whenResolvedResolvers.get(i);
          promise.scheduleCallback(result, callback, resolver);
        }
      }
    }
  }

  @CompilationFinal public static SClass pairClass;
  public static void setPairClass(final SClass cls) {
    pairClass = cls;
  }
}
