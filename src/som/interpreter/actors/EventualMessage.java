package som.interpreter.actors;

import java.util.concurrent.RecursiveAction;

import som.compiler.AccessModifier;
import som.interpreter.Types;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;


public final class EventualMessage extends RecursiveAction {
  private static final long serialVersionUID = -7994739264831630827L;

  private Actor target;
  private final SSymbol  selector;
  private final Object[] args;
  private final SResolver resolver;

  public EventualMessage(final Actor actor, final SSymbol selector,
      final Object[] args, final SResolver resolver) {
    this.target   = actor;
    this.selector = selector;
    this.args     = args;
    this.resolver = resolver;
    assert resolver != null;
  }

  public void setReceiverForEventualPromiseSend(final Object rcvr) {
    args[0] = rcvr;
  }

  public void setTargetActorForEventualPromiseSend(final Actor target) {
    this.target = target;
  }

  public boolean isReceiverSet() {
    return args[0] != null;
  }

  @Override
  protected void compute() {
    actorThreadLocal.set(target);

    try {
      executeMessage();
    } catch (Throwable t) {
      t.printStackTrace();
    }

    actorThreadLocal.set(null);
    target.enqueueNextMessageForProcessing();
  }

  protected void executeMessage() {
    CompilerAsserts.neverPartOfCompilation("Not Optimized! But also not sure it can be part of compilation anyway");

    Object rcvrObj = args[0];
    assert rcvrObj != null;

    Object result;
    assert !(rcvrObj instanceof SFarReference);

    Dispatchable disp = Types.getClassOf(rcvrObj).
        lookupMessage(selector, AccessModifier.PUBLIC);
    result = disp.invoke(args);

    resolver.resolve(result);
  }

  public static Actor getActorCurrentMessageIsExecutionOn() {
    return actorThreadLocal.get();
  }

  public static void setMainActor(final Actor actor) {
    actorThreadLocal.set(actor);
  }

  @Override
  public String toString() {
    return "EMsg(" + selector.toString() + ", " + Arrays.toString(args) + ")";
  }

  private static final ThreadLocal<Actor> actorThreadLocal = new ThreadLocal<Actor>();
}
