package tools.concurrency;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node.Child;

import som.VM;
import som.interpreter.SomLanguage;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.ResolvePromiseNode;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.actors.WrapReferenceNode;
import som.interpreter.actors.WrapReferenceNodeGen;
import som.vm.Symbols;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import tools.concurrency.TracingActors.TracingActor;

public class Assertion {
  static SImmutableObject assertionModule;
  SBlock statement;
  SResolver result;

  @Child protected WrapReferenceNode wrapper;

  public Assertion(final SBlock statement, final SResolver result) {
    super();
    this.result = result;
    this.statement = statement;
    CompilerDirectives.transferToInterpreter();
    wrapper = WrapReferenceNodeGen.create();
  }

  public static void setAssertionModule(final SImmutableObject assertionClass) {
    Assertion.assertionModule = assertionClass;
  }


  /***
   * @return returns a boolean indicating whether the Assertion should be checked again next time.
   */
  public boolean evaluate(final TracingActor actor, final EventualMessage msg, final ForkJoinPool actorPool) {

    Object o = statement.getMethod().invoke(new Object[] {statement});

    if (o instanceof SPromise) {
      promise((SPromise) o);
    } else {
      boolean result = (boolean) o;
      if (!result) {
        fail(actorPool);
      } else {
        success(actorPool);
      }
    }

    return false;
  }

  protected void fail(final ForkJoinPool actorPool) {
    ResolvePromiseNode.resolve(Resolution.SUCCESSFUL, wrapper,
        result.getPromise(), false,
        result.getPromise().getOwner(), actorPool, false);
  }

  protected void success(final ForkJoinPool actorPool) {
    ResolvePromiseNode.resolve(Resolution.SUCCESSFUL, wrapper,
        result.getPromise(), true,
        result.getPromise().getOwner(), actorPool, false);
  }

  protected void promise(final SPromise result) {
    result.addChainedPromise(this.result.getPromise());
  }

  public static class RemoteAssertion extends Assertion {
    final Object target;
    public RemoteAssertion(final SBlock statement, final SResolver result, final Object target) {
      super(statement, result);
      this.target = target;
    }

    @Override
    public boolean evaluate(final TracingActor actor, final EventualMessage msg, final ForkJoinPool actorPool) {
      Object o = statement.getMethod().invoke(new Object[] {statement, createWrapper(target)});

      if (o instanceof SPromise) {
        promise((SPromise) o);
      } else {
        boolean result = (boolean) o;
        if (!result) {
          fail(actorPool);
        } else {
          success(actorPool);
        }
      }

      return false;
    }

    private Object createWrapper(final Object target) {
      CompilerDirectives.transferToInterpreter();
      VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");
      SInvokable disp = (SInvokable) assertionModule.getSOMClass().lookupPrivate(
          Symbols.symbolFor("instantiateRemoteAssertion:"),
          assertionModule.getSOMClass().getMixinDefinition().getMixinId());
      return disp.invoke(new Object[] {assertionModule, target});
    }
  }

  public static class UntilAssertion extends Assertion {
    SBlock until;

    public UntilAssertion(final SBlock statement, final SResolver result, final SBlock until) {
      super(statement, result);
      this.until = until;
    }

    @Override
    public boolean evaluate(final TracingActor actor, final EventualMessage msg, final ForkJoinPool actorPool) {
      boolean result = (boolean) until.getMethod().invoke(new Object[] {until});
      if (!result) {
        boolean result2 = (boolean) statement.getMethod().invoke(new Object[] {statement});
        if (!result2) {
          fail(actorPool);
        } else {
          return true;
        }
      } else {
        success(actorPool);
      }
      return false;
    }
  }

  public static class ReleaseAssertion extends Assertion{
    SBlock release;

    public ReleaseAssertion(final SBlock statement, final SResolver result, final SBlock release) {
      super(statement, result);
      this.release = release;
    }

    @Override
    public boolean evaluate(final TracingActor actor, final EventualMessage msg, final ForkJoinPool actorPool) {
      boolean result = (boolean) release.getMethod().invoke(new Object[] {release});
      if (!result) {
        fail(actorPool);
        return false;
      }

      boolean result2 = (boolean) statement.getMethod().invoke(new Object[] {statement});
      if (!result2) {
        return true;
      } else {
        success(actorPool);
      }
      return false;
    }
  }

  public static class NextAssertion extends Assertion{
    boolean dormant = true;

    public NextAssertion(final SBlock statement, final SResolver result) {
      super(statement, result);
    }

    @Override
    public boolean evaluate(final TracingActor actor, final EventualMessage msg,
        final ForkJoinPool actorPool) {

      if (dormant) {
        dormant = false;
        return true;
      }
      return super.evaluate(actor, msg, actorPool);
    }
  }

  public static class FutureAssertion extends Assertion{
    protected static Set<FutureAssertion> futureAssertions = new HashSet<>();

    public FutureAssertion(final SBlock statement, final SResolver result) {
      super(statement, result);
      synchronized (futureAssertions) {
        futureAssertions.add(this);
      }
    }

    @Override
    public boolean evaluate(final TracingActor actor, final EventualMessage msg, final ForkJoinPool actorPool) {
      boolean result = (boolean) statement.getMethod().invoke(new Object[] {statement});

      if (result) {
        synchronized (futureAssertions) {
          futureAssertions.remove(this);
        }
        success(actorPool);
      } else {
        return true;
      }
      return false;
    }

    public static void checkFutureAssertions() {
      if (futureAssertions.size() > 0) {
        for (FutureAssertion fa: futureAssertions) {
          if (fa instanceof ResultUsedAssertion) {
            ((ResultUsedAssertion) fa).finalCheck();
          } else {
            fa.fail(fa.statement.getMethod().getCallTarget().getRootNode().getLanguage(SomLanguage.class).getVM().getActorPool());
          }
        }
      }
    }
  }

  public static class GloballyAssertion extends Assertion{

    public GloballyAssertion(final SBlock statement, final SResolver result) {
      super(statement, result);
    }

    // TODO success on termination?, is this necessary
    // would have to be done like the future assertions, i.e. keep a list/set of all the Global stuff
    // only get's removed if a check fails

    @Override
    public boolean evaluate(final TracingActor actor, final EventualMessage msg, final ForkJoinPool actorPool) {
      boolean result = (boolean) statement.getMethod().invoke(new Object[] {statement});
      if (!result) {
        fail(actorPool);
      } else {
        return true;
      }
      return false;
    }
  }

  public static class ResultUsedAssertion extends FutureAssertion{
    final SPromise checkedPromise;

    public ResultUsedAssertion(final SPromise statement, final SResolver result) {
      super(null, result);
      this.checkedPromise = statement;
    }

    @Override
    public boolean evaluate(final TracingActor actor, final EventualMessage msg, final ForkJoinPool actorPool) {
      synchronized (checkedPromise) {
        if (checkedPromise.isResultUsed()) {
          synchronized (futureAssertions) {
            futureAssertions.remove(this);
          }
          success(actorPool);
        } else {
          return true;
        }
      }
      return false;
    }

    public void finalCheck() {
      synchronized (checkedPromise) {
        if (!checkedPromise.isResultUsed()) {
          fail(checkedPromise.getSOMClass().getMethods()[0].getInvokable().getRootNode().getLanguage(SomLanguage.class).getVM().getActorPool());
        }
      }
    }
  }
}
