package tools.asyncstacktraces;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.Output;
import som.interpreter.actors.Actor;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.nodes.ExpressionNode;
import tools.SourceCoordinate;


public class ShadowStackEntry {

  protected final ShadowStackEntry previous;
  protected final ExpressionNode   expression;

  public static long numberOfAllocations;

  public static final boolean ALLOCATION_COUNT = false;

  public static ShadowStackEntry create(final ShadowStackEntry previous,
      final ExpressionNode expr) {
    return new ShadowStackEntry(previous, expr);
  }

  public static ShadowStackEntry createAtAsyncSend(final ShadowStackEntry previous,
      final ExpressionNode expr) {
    return new EntryAtMessageSend(previous, expr);
  }

  public static ShadowStackEntry createAtPromiseResolution(final ShadowStackEntry previous,
      final ExpressionNode expr) {
    return new EntryForPromiseResolution(previous, expr);
  }

  protected ShadowStackEntry(final ShadowStackEntry previous, final ExpressionNode expr) {
    this.previous = previous;
    this.expression = expr;
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
      currentEntry = currentEntry.previous;
    }
    Output.print(sb.toString());
  }

  public Actor printOn(final StringBuilder sb, final Actor currentActor) {
    SourceSection nodeSS = getSourceSection();
    String location =
        nodeSS.getSource().getName() + SourceCoordinate.getLocationQualifier(nodeSS);
    String method = getRootNode().getName();
    sb.append("   ");
    sb.append(method);
    sb.append(':');
    sb.append(location);
    sb.append('\n');
    return currentActor;
  }

  public ShadowStackEntry getPrevious() {
    return previous;
  }

  public RootNode getRootNode() {
    return expression.getRootNode();
  }

  public SourceSection getSourceSection() {
    return expression.getSourceSection();
  }

  public boolean isAsync() {
    return false;
  }

  private static final class EntryAtMessageSend extends ShadowStackEntry {
    protected final Actor sender;

    private EntryAtMessageSend(final ShadowStackEntry previous, final ExpressionNode expr) {
      super(previous, expr);
      this.sender = getCurrentActorOrNull();
    }

    @Override
    public Actor printOn(final StringBuilder sb, final Actor currentActor) {
      if (sender != currentActor) {
        sb.append(sender.toString());
        sb.append(" (hash=");
        sb.append(sender.hashCode());
        sb.append(")\n");
      }
      super.printOn(sb, currentActor);
      return sender;
    }

    @Override
    public boolean isAsync() {
      return true;
    }
  }

  private static final class EntryForPromiseResolution extends ShadowStackEntry {
    private EntryForPromiseResolution(final ShadowStackEntry previous,
        final ExpressionNode expr) {
      super(previous, expr);
    }

    @Override
    public boolean isAsync() {
      return true;
    }
  }
}
