package som.interpreter.actors;

import java.util.concurrent.RecursiveAction;

import som.interpreter.actors.SPromise.Resolver;
import som.vmobjects.SSymbol;


public class EventualMessage extends RecursiveAction {
  private static final long serialVersionUID = -7994739264831630827L;

  private final Actor    target;
  private final SSymbol  selector;
  private final Object[] args;
  private final Resolver resolver;

  public EventualMessage(final Actor actor, final SSymbol selector,
      final Object[] args) {
    this(actor, selector, args, null);
  }

  public EventualMessage(final Actor actor, final SSymbol selector,
      final Object[] args, final Resolver resolver) {
    this.target   = actor;
    this.selector = selector;
    this.args     = args;
    this.resolver = resolver;
  }

  @Override
  protected void compute() {
    actorThreadLocal.set(target);
    SFarReference rcvr = (SFarReference) args[0];

    // TODO: need to get hold of some AST/RootNode to start execution
    //       do i want to cache the lookup?
    Object result = rcvr.directSend(selector, args);

    if (resolver != null) {
      resolver.resolve(result);
    }

    actorThreadLocal.set(null);
    target.enqueueNextMessageForProcessing();
  }

  public static Actor getActorCurrentMessageIsExecutionOn() {
    return actorThreadLocal.get();
  }

  private static final ThreadLocal<Actor> actorThreadLocal = new ThreadLocal<Actor>();
}
