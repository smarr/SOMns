package tools.concurrency;

import java.util.HashSet;
import java.util.Set;

import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SPromise;
import som.vmobjects.SBlock;
import tools.concurrency.TracingActors.TracingActor;

public class Assertion {
  SBlock statement;
  String message;

  public Assertion(final SBlock statement) {
    super();
    this.statement = statement;
  }

  public Assertion(final SBlock statement, final String msg) {
    super();
    this.message = msg;
    this.statement = statement;
  }

  public void evaluate(final TracingActor actor, final EventualMessage msg) {
    boolean result = (boolean) statement.getMethod().invoke(new Object[] {statement});
    if (!result) {
      throwError();
    }
  }

  protected void throwError() {
    if (message == null) {
      throw new AssertionError(statement.toString());
    } else {
      throw new AssertionError(message);
    }
  }


  public static class UntilAssertion extends Assertion{
    SBlock until;

    public UntilAssertion(final SBlock statement, final SBlock until) {
      super(statement);
      this.until = until;
    }

    public UntilAssertion(final SBlock statement, final SBlock until, final String msg) {
      super(statement, msg);
      this.until = until;
    }

    @Override
    public void evaluate(final TracingActor actor, final EventualMessage msg) {
      boolean result = (boolean) until.getMethod().invoke(new Object[] {until});
      if (!result) {
        boolean result2 = (boolean) statement.getMethod().invoke(new Object[] {statement});
        if (!result2) {
          throwError();
        } else {
          actor.addAssertion(this);
        }
      }
    }
  }

  public static class ReleaseAssertion extends Assertion{
    SBlock release;

    public ReleaseAssertion(final SBlock statement, final SBlock release) {
      super(statement);
      this.release = release;
    }

    public ReleaseAssertion(final SBlock statement, final SBlock release, final String msg) {
      super(statement, msg);
      this.release = release;
    }

    @Override
    public void evaluate(final TracingActor actor, final EventualMessage msg) {
      boolean result = (boolean) release.getMethod().invoke(new Object[] {release});
      if (!result) {
        throwError();
      }

      boolean result2 = (boolean) statement.getMethod().invoke(new Object[] {statement});
      if (!result2) {
        actor.addAssertion(this);
      }
    }
  }

  public static class NextAssertion extends Assertion{
    public NextAssertion(final SBlock statement) {
      super(statement);
    }

    public NextAssertion(final SBlock statement, final String msg) {
      super(statement, msg);
    }
  }

  public static class FutureAssertion extends Assertion{
    protected static Set<FutureAssertion> futureAssertions = new HashSet<>();

    public FutureAssertion(final SBlock statement) {
      super(statement);
      synchronized (futureAssertions) {
        futureAssertions.add(this);
      }
    }

    public FutureAssertion(final SBlock statement, final String msg) {
      super(statement, msg);
      synchronized (futureAssertions) {
        futureAssertions.add(this);
      }
    }

    @Override
    public void evaluate(final TracingActor actor, final EventualMessage msg) {
      boolean result = (boolean) statement.getMethod().invoke(new Object[] {statement});
      if (result) {
        synchronized (futureAssertions) {
          futureAssertions.remove(this);
        }
      } else {
        actor.addAssertion(this);
      }
    }

    public void finalCheck() {
      boolean result = (boolean) statement.getMethod().invoke(new Object[] {statement});
      if (!result) {
        throwError();
      }
    }

    public static void checkFutureAssertions() {
      if (futureAssertions.size() > 0) {
        for (FutureAssertion fa: futureAssertions) {
          fa.finalCheck();
        }
      }
    }
  }

  public static class GloballyAssertion extends Assertion{
    public GloballyAssertion(final SBlock statement) {
      super(statement);
    }

    public GloballyAssertion(final SBlock statement, final String msg) {
      super(statement, msg);
    }

    @Override
    public void evaluate(final TracingActor actor, final EventualMessage msg) {
      boolean result = (boolean) statement.getMethod().invoke(new Object[] {statement});
      if (!result) {
        throwError();
      } else {
        actor.addAssertion(this);
      }
    }
  }

  public static class ResultUsedAssertion extends FutureAssertion{
    final SPromise checkedPromise;

    public ResultUsedAssertion(final SPromise statement) {
      super(null);
      this.checkedPromise = statement;
    }

    public ResultUsedAssertion(final SPromise statement, final String msg) {
      super(null, msg);
      this.checkedPromise = statement;
    }

    @Override
    public void evaluate(final TracingActor actor, final EventualMessage msg) {
      synchronized (checkedPromise) {
        if (checkedPromise.isResultUsed()) {
          synchronized (futureAssertions) {
            futureAssertions.remove(this);
          }
        } else {
          actor.addAssertion(this);
        }
      }
    }

    @Override
    public void finalCheck() {
      synchronized (checkedPromise) {
        if (!checkedPromise.isResultUsed()) {
          throwError();
        }
      }
    }
  }
}
