package tools.asyncstacktraces;

import com.oracle.truffle.api.source.SourceSection;

import som.Output;
import som.interpreter.actors.Actor;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.nodes.ExpressionNode;
import tools.SourceCoordinate;


public abstract class ShadowStackEntry {

  protected final ShadowStackEntry previousEntry;
  protected final ExpressionNode   expression;
  public static long               numberOfAllocations = 0;
  public static final boolean      ALLOCATION_COUNT    = false;

  public ShadowStackEntry(final ShadowStackEntry previousEntry,
      final ExpressionNode expression) {
    this.previousEntry = previousEntry;
    this.expression = expression;
    if (ALLOCATION_COUNT) {
      numberOfAllocations++;
    }
  }

  public static Actor getCurrentActorOrNull() {
    Thread t = Thread.currentThread();
    if (t instanceof ActorProcessingThread) {
      return EventualMessage.getActorCurrentMessageIsExecutionOn();
    } else {
      return null;
    }
  }

  public void printAsyncStackTrace() {
    ShadowStackEntry currentEntry = this;
    StringBuilder sb = new StringBuilder();
    Actor currentActor = getCurrentActorOrNull();
    while (currentEntry != null) {
      currentActor = currentEntry.printOn(sb, currentActor);
      currentEntry = currentEntry.previousEntry;
    }
    Output.print(sb.toString());
  }

  public Actor printOn(final StringBuilder sb, final Actor currentActor) {
    SourceSection nodeSS = expression.getSourceSection();
    String location =
        nodeSS.getSource().getName() + SourceCoordinate.getLocationQualifier(nodeSS);
    String method = expression.getRootNode().getName();
    sb.append(' ');
    sb.append(method);
    sb.append(':');
    sb.append(location);
    sb.append('\n');
    return currentActor;
  }

}
