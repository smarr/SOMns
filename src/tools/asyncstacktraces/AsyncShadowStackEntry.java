package tools.asyncstacktraces;

import som.interpreter.actors.Actor;
import som.interpreter.nodes.ExpressionNode;


public class AsyncShadowStackEntry extends ShadowStackEntry {
  protected final Actor actor;

  public AsyncShadowStackEntry(final ShadowStackEntry previousEntry,
      final ExpressionNode expression) {
    super(previousEntry, expression);
    this.actor = getCurrentActorOrNull();
  }

  @Override
  public Actor printOn(final StringBuilder sb, final Actor currentActor) {
    if (actor != currentActor) {
      sb.append(actor.toString());
      sb.append(" (hash=");
      sb.append(actor.hashCode());
      sb.append(")\n");
    }
    super.printOn(sb, currentActor);
    return actor;
  }

  @Override
  public boolean isAsync() {
    return true;
  }
}
