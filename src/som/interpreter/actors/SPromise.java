package som.interpreter.actors;

import java.util.ArrayList;

import som.vm.NotYetImplementedException;
import som.vm.Symbols;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;


public final class SPromise extends SAbstractObject {
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
  private final ArrayList<SBlock> whenResolved;
  private final ArrayList<SBlock> whenBroken;
  private Object  value;
  private boolean resolved;

  // the owner of this promise, on which all call backs are scheduled
  private final Actor owner;


  public SPromise(final Actor owner) {
    this.owner = owner;
    whenResolved = new ArrayList<>(2);
    whenBroken   = new ArrayList<>(1);
    resolved     = false;
  }

  @Override
  public SClass getSOMClass() {
    return promiseClass;
  }

  public static void setSOMClass(final SClass cls) {
    assert promiseClass == null;
    promiseClass = cls;
  }

  public synchronized void whenResolved(final SBlock block) {
    assert block.getMethod().getNumberOfArguments() == 2;
    if (resolved) {
      scheduleCallback(value, block);
    } else {
      whenResolved.add(block);
    }
  }

  protected void scheduleCallback(final Object result, final SBlock callback) {
    EventualMessage msg = new EventualMessage(owner, Resolver.valueSelector,
        new Object[] {callback, result});
    owner.enqueueMessage(msg);
  }

  public static final class Resolver {
    private final SPromise promise;
    private static final SSymbol valueSelector = Symbols.symbolFor("value:");

    public Resolver(final SPromise promise) {
      this.promise = promise;
    }

    public void onError() {
      throw new NotYetImplementedException(); // TODO: implement
    }

    public void resolve(final Object result) {
      assert promise.value == null;

      synchronized (promise) {
        promise.value    = result;
        promise.resolved = true;
      }

      for (SBlock callback : promise.whenResolved) {
        promise.scheduleCallback(result, callback);
      }
    }
  }
}
