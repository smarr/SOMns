package tools.asyncstacktraces;

import com.oracle.truffle.api.source.SourceSection;

import som.Output;
import som.interpreter.actors.Actor;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.nodes.ExpressionNode;
import tools.SourceCoordinate;


public class ShadowStackEntry {

  protected final ShadowStackEntry previousEntry;
  protected final ExpressionNode   expression;
  protected final Actor            actor;

  public ShadowStackEntry(final ShadowStackEntry previousEntry,
      final ExpressionNode expression) {
    this.previousEntry = previousEntry;
    this.expression = expression;
    Thread t = Thread.currentThread();
    if (t instanceof ActorProcessingThread) {
      this.actor = EventualMessage.getActorCurrentMessageIsExecutionOn();
    } else {
      this.actor = null;
    }

  }

  public void print() {
    ShadowStackEntry currentEntry = this;
    StringBuilder sb = new StringBuilder();
    Actor currentActor = actor;
    while (currentEntry != null) {
      if (currentActor != currentEntry.actor) {
        sb.append(currentEntry.actor.toString());
        sb.append(" (");
        sb.append(currentEntry.actor.hashCode());
        sb.append(")\n");
        currentActor = currentEntry.actor;
      }
      SourceSection nodeSS = currentEntry.expression.getSourceSection();
      String location =
          nodeSS.getSource().getName() + SourceCoordinate.getLocationQualifier(nodeSS);
      String method = currentEntry.expression.getRootNode().getName();
      sb.append(method);
      sb.append(':');
      sb.append(location);
      sb.append('\n');
      currentEntry = currentEntry.previousEntry;
    }

    Output.print(sb.toString());
  }
}
