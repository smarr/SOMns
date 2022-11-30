package tools.debugger.asyncstacktraces;

import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SPromise;
import som.vm.VmSettings;


public class ShadowStackEntry {

  protected ShadowStackEntry previous;
  protected final Node       expression;
  protected final long       actorId;

  public static long numberOfAllocations;

  public static final boolean ALLOCATION_COUNT = false;

  public Node getExpression() {
    return expression;
  }

  public ShadowStackEntry getPreviousShadowStackEntry() {
    return previous;
  }

  public static ShadowStackEntry createTop(final Node expr) {
    return new ShadowStackEntry(null, expr);
  }

  public static ShadowStackEntry create(final ShadowStackEntry previous,
      final Node expr) {
    assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || previous != null;
    return new ShadowStackEntry(previous, unwrapNodeIfNecessary(expr));
  }

  public static ShadowStackEntry createAtAsyncSend(final ShadowStackEntry previous,
      final Node expr) {
    assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || previous != null;
    return new EntryAtMessageSend(previous, unwrapNodeIfNecessary(expr));
  }

  public static ShadowStackEntry createAtPromiseResolution(final ShadowStackEntry previous,
      final Node expr, final EntryForPromiseResolution.ResolutionLocation resolutionType,
      final String resolutionValue, SPromise promiseOrNull) {
    assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || previous != null;
    return new EntryForPromiseResolution(previous, unwrapNodeIfNecessary(expr), resolutionType,
        resolutionValue, promiseOrNull);
  }

  public static ShadowStackEntry createAtPromiseResolution(final ShadowStackEntry previous,
                                                           final Node expr, final EntryForPromiseResolution.ResolutionLocation resolutionType,
                                                           final String resolutionValue){
    return ShadowStackEntry.createAtPromiseResolution(previous, expr, resolutionType,resolutionValue,null);
  }

  public static Node unwrapNodeIfNecessary(final Node node) {
    if (node instanceof WrapperNode) {
      return ((WrapperNode) node).getDelegateNode();
    } else {
      return node;
    }
  }

  protected ShadowStackEntry(final ShadowStackEntry previous, final Node expr) {
    assert VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE;
    this.previous = previous;
    this.expression = expr;
    if (ALLOCATION_COUNT) {
      numberOfAllocations++;
    }
    //this.actorId = getCurrentActor();
    this.actorId = 0;
  }

  public long getCurrentActor() {
    Thread t = Thread.currentThread();
    if (t instanceof ActorProcessingThread) {
      return EventualMessage.getActorCurrentMessageIsExecutionOn().getId();
    } else {
      return -1L;
    }
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

  public void setPreviousShadowStackEntry(final ShadowStackEntry maybeEntry) {
    previous = maybeEntry;
  }

  public static final class EntryAtMessageSend extends ShadowStackEntry {

    private EntryAtMessageSend(final ShadowStackEntry previous, final Node expr) {
      super(previous, expr);
    }
  }

  public static final class EntryForPromiseResolution extends ShadowStackEntry {
    public enum ResolutionLocation {
      SUCCESSFUL("resolved with a value"), ERROR("resolved with an error"),
      CHAINED("resolved with a promise"), ON_WHEN_RESOLVED_BLOCK("on whenResolved block"),
      ON_WHEN_RESOLVED("on whenResolved"), ON_WHEN_RESOLVED_ERROR("on whenResolved error"),
      ON_RECEIVE_MESSAGE("on receive message"), ON_SCHEDULE_PROMISE("on schedule");

      private final String label;

      ResolutionLocation(final String label) {
        this.label = label;
      }

      public String getValue() {
        return label;
      }
    }

    public ResolutionLocation resolutionLocation;
    public String             resolutionValue;
    public SPromise           promiseGroupOrNull = null;

    private EntryForPromiseResolution(final ShadowStackEntry previous,
        final Node expr, final ResolutionLocation resolutionLocation,
        final String resolutionValue) {
      super(previous, expr);
      this.resolutionLocation = resolutionLocation;
      this.resolutionValue = resolutionValue;
    }

    private EntryForPromiseResolution(final ShadowStackEntry previous,
                                      final Node expr, final ResolutionLocation resolutionLocation,
                                      final String resolutionValue,  SPromise promiseGroup) {
      this(previous,expr,resolutionLocation,resolutionValue);
      this.promiseGroupOrNull = promiseGroup;
    }

    public boolean isPromiseGroup() { return promiseGroupOrNull != null; }

    @Override
    public boolean isAsync() {
      return true;
    }

  }
}
