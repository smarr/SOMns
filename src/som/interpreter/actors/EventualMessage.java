package som.interpreter.actors;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.ReceivedMessage.ReceivedCallback;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActivityThread;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;


public abstract class EventualMessage {
  protected final Object[]       args;
  protected final SResolver      resolver;
  protected final RootCallTarget onReceive;

  /**
   * Contains the messageId for Kompos tracing.
   * This field is reused for snapshotting. It then contains the snapshot version at send
   * time. The snapshot version is used to determine whether the message needs to be
   * serialized.
   */
  @CompilationFinal protected long messageId;

  /**
   * Indicates the case that an asynchronous message has a receiver breakpoint.
   * It is not final because its value can be updated so that other breakpoints
   * can reuse the stepping strategy implemented for message receiver breakpoints.
   */
  protected boolean haltOnReceive;

  /**
   * Indicates that the implicit promise, corresponding to this message,
   * has a promise resolver breakpoint.
   */
  private final boolean haltOnResolver;

  protected EventualMessage(final Object[] args,
      final SResolver resolver, final RootCallTarget onReceive,
      final boolean haltOnReceive, final boolean haltOnResolver) {
    this.args = args;
    this.resolver = resolver;
    this.onReceive = onReceive;
    this.haltOnReceive = haltOnReceive;
    this.haltOnResolver = haltOnResolver;
    if (VmSettings.KOMPOS_TRACING) {
      this.messageId = TracingActivityThread.newEntityId();
    } else {
      this.messageId = 0;
    }
    assert onReceive.getRootNode() instanceof ReceivedMessage
        || onReceive.getRootNode() instanceof ReceivedCallback;
  }

  public abstract Actor getTarget();

  public abstract Actor getSender();

  public SResolver getResolver() {
    return resolver;
  }

  public final long getMessageId() {
    return messageId;
  }

  public abstract SSymbol getSelector();

  public SourceSection getTargetSourceSection() {
    return onReceive.getRootNode().getSourceSection();
  }

  @TruffleBoundary
  // TODO: can we establish a structure for this? at the moment, we have an
  // indirection here, which leads us to a serializer that's not compilation
  // final, I think
  public long forceSerialize(final SnapshotBuffer sb) {
    if (sb.getRecord().containsObject(this)) {
      return sb.getRecord().getObjectPointer(this);
    }
    ReceivedRootNode rm = (ReceivedRootNode) this.onReceive.getRootNode();
    return rm.getSerializer().execute(this, sb);
  }

  /**
   * A message to a known receiver that is to be executed on the actor owning
   * the receiver.
   *
   * ARGUMENTS: are wrapped eagerly on message creation
   */
  public abstract static class AbstractDirectMessage extends EventualMessage {
    private final SSymbol selector;
    private final Actor   target;
    private final Actor   sender;

    protected AbstractDirectMessage(final Actor target, final SSymbol selector,
        final Object[] arguments, final Actor sender, final SResolver resolver,
        final RootCallTarget onReceive, final boolean triggerMessageReceiverBreakpoint,
        final boolean triggerPromiseResolverBreakpoint) {
      super(arguments, resolver, onReceive, triggerMessageReceiverBreakpoint,
          triggerPromiseResolverBreakpoint);
      this.selector = selector;
      this.sender = sender;
      this.target = target;

      if (VmSettings.SNAPSHOTS_ENABLED && !VmSettings.REPLAY) {
        this.messageId = ActorProcessingThread.currentThread().getSnapshotId();
      }

      assert target != null;
      assert !(args[0] instanceof SFarReference) : "needs to be guaranted by call to this constructor";
      assert !(args[0] instanceof SPromise);
    }

    /**
     * Constructor for non TracingActivityThreads, i.e. used for the initial start message or
     * TimerPrim
     */
    protected AbstractDirectMessage(final Actor target, final SSymbol selector,
        final Object[] arguments, final Actor sender, final SResolver resolver,
        final RootCallTarget onReceive) {
      super(arguments, resolver, onReceive, false, false);
      this.selector = selector;
      this.sender = sender;
      this.target = target;

      if (VmSettings.SNAPSHOTS_ENABLED && !VmSettings.REPLAY) {
        this.messageId = SnapshotBackend.getSnapshotVersion();
      }

      assert target != null;
      assert !(args[0] instanceof SFarReference) : "needs to be guaranted by call to this constructor";
      assert !(args[0] instanceof SPromise);
    }

    @Override
    public Actor getTarget() {
      assert target != null;
      return target;
    }

    @Override
    public Actor getSender() {
      assert sender != null;
      return sender;
    }

    @Override
    public SSymbol getSelector() {
      return selector;
    }

    @Override
    public boolean getHaltOnPromiseMessageResolution() {
      return false;
    }

    @Override
    public String toString() {
      String t = target.toString();
      return "DirectMsg(" + selector.toString() + ", "
          + Arrays.toString(args) + ", " + t
          + ", sender: " + (sender == null ? "" : sender.toString()) + ")";
    }
  }

  public static final class DirectMessage extends AbstractDirectMessage {
    public DirectMessage(final Actor target, final SSymbol selector,
        final Object[] arguments, final Actor sender, final SResolver resolver,
        final RootCallTarget onReceive, final boolean triggerMessageReceiverBreakpoint,
        final boolean triggerPromiseResolverBreakpoint) {
      super(target, selector, arguments, sender, resolver, onReceive,
          triggerMessageReceiverBreakpoint, triggerPromiseResolverBreakpoint);
    }

    /**
     * Constructor for non TracingActivityThreads, i.e. used for the initial start message or
     * TimerPrim
     */
    public DirectMessage(final Actor target, final SSymbol selector,
        final Object[] arguments, final Actor sender, final SResolver resolver,
        final RootCallTarget onReceive) {
      super(target, selector, arguments, sender, resolver, onReceive);
    }
  }

  protected static Actor determineTargetAndWrapArguments(final Object[] arguments,
      Actor target, final Actor currentSender, final Actor originalSender) {
    VM.thisMethodNeedsToBeOptimized("not optimized for compilation");

    // target: the owner of the promise that just got resolved
    // however, if a promise gets resolved to a far reference
    // we need to redirect the message to the owner of that far reference

    Object receiver = target.wrapForUse(arguments[0], currentSender, null);
    assert !(receiver instanceof SPromise) : "TODO: handle this case as well?? Is it possible? didn't think about it";

    if (receiver instanceof SFarReference) {
      // now we are about to send a message to a far reference, so, it
      // is better to just redirect the message back to the current actor
      target = ((SFarReference) receiver).getActor();
      receiver = ((SFarReference) receiver).getValue();
    }

    arguments[0] = receiver;

    assert !(receiver instanceof SFarReference) : "this should not happen, because we need to redirect messages to the other actor, and normally we just unwrapped this";
    assert !(receiver instanceof SPromise);

    for (int i = 1; i < arguments.length; i++) {
      arguments[i] = target.wrapForUse(arguments[i], originalSender, null);
    }

    return target;
  }

  /** A message send after a promise got resolved. */
  public abstract static class PromiseMessage extends EventualMessage {
    public static final int PROMISE_RCVR_IDX  = 0;
    public static final int PROMISE_VALUE_IDX = 1;

    protected final Actor originalSender; // initial owner of the arguments

    public PromiseMessage(final Object[] arguments, final Actor originalSender,
        final SResolver resolver, final RootCallTarget onReceive,
        final boolean triggerMessageReceiverBreakpoint,
        final boolean triggerPromiseResolverBreakpoint) {
      super(arguments, resolver, onReceive, triggerMessageReceiverBreakpoint,
          triggerPromiseResolverBreakpoint);
      this.originalSender = originalSender;

      if (VmSettings.SNAPSHOTS_ENABLED && !VmSettings.REPLAY) {
        this.messageId = ActorProcessingThread.currentThread().getSnapshotId();
      }
    }

    public abstract void resolve(Object rcvr, Actor target, Actor sendingActor);

    @Override
    public final Actor getSender() {
      assert originalSender != null;
      return originalSender;
    }

    public abstract SPromise getPromise();

    /**
     * Used for Fixup in deserialization.
     */
    public abstract void setPromise(SPromise promise);

    public abstract boolean isDelivered();

    @Override
    public boolean getHaltOnPromiseMessageResolution() {
      return getPromise().getHaltOnResolution();
    }
  }

  /**
   * A message that was send with <-: to a promise, and will be delivered
   * after the promise is resolved.
   */
  public abstract static class AbstractPromiseSendMessage extends PromiseMessage {
    private final SSymbol                selector;
    protected Actor                      target;
    protected Actor                      finalSender;
    @CompilationFinal protected SPromise originalTarget;

    protected AbstractPromiseSendMessage(final SSymbol selector,
        final Object[] arguments, final Actor originalSender,
        final SResolver resolver, final RootCallTarget onReceive,
        final boolean triggerMessageReceiverBreakpoint,
        final boolean triggerPromiseResolverBreakpoint) {
      super(arguments, originalSender, resolver, onReceive, triggerMessageReceiverBreakpoint,
          triggerPromiseResolverBreakpoint);
      this.selector = selector;
      assert (args[0] instanceof SPromise);
      this.originalTarget = (SPromise) args[0];
      if (VmSettings.SNAPSHOTS_ENABLED && !VmSettings.REPLAY) {
        this.messageId = ActorProcessingThread.currentThread().getSnapshotId();
      }
    }

    @Override
    public void resolve(final Object rcvr, final Actor target, final Actor sendingActor) {
      determineAndSetTarget(rcvr, target, sendingActor);
    }

    private void determineAndSetTarget(final Object rcvr, final Actor target,
        final Actor sendingActor) {
      VM.thisMethodNeedsToBeOptimized("not optimized for compilation");

      args[0] = rcvr;
      Actor finalTarget =
          determineTargetAndWrapArguments(args, target, sendingActor, originalSender);

      this.target = finalTarget; // for sends to far references, we need to adjust the target
      this.finalSender = sendingActor;
      if (VmSettings.SNAPSHOTS_ENABLED && !VmSettings.REPLAY) {
        this.messageId =
            Math.max(ActorProcessingThread.currentThread().getSnapshotId(), this.messageId);
      }
    }

    @Override
    public SSymbol getSelector() {
      return selector;
    }

    @Override
    public Actor getTarget() {
      assert target != null;
      return target;
    }

    @Override
    public boolean isDelivered() {
      return target != null;
    }

    @Override
    public String toString() {
      String t;
      if (target == null) {
        t = "null";
      } else {
        t = target.toString();
      }
      return "PSendMsg(" + selector.toString() + " " + Arrays.toString(args) + ", " + t
          + ", sender: " + (finalSender == null ? "" : finalSender.toString()) + ")";
    }

    @Override
    public SPromise getPromise() {
      return originalTarget;
    }

    @Override
    public final void setPromise(final SPromise promise) {
      assert VmSettings.SNAPSHOTS_ENABLED;
      assert promise != null;
      this.originalTarget = promise;
    }

    public Actor getFinalSender() {
      return finalSender;
    }
  }

  public static final class PromiseSendMessage extends AbstractPromiseSendMessage {
    public PromiseSendMessage(final SSymbol selector, final Object[] arguments,
        final Actor originalSender, final SResolver resolver, final RootCallTarget onReceive,
        final boolean triggerMessageReceiverBreakpoint,
        final boolean triggerPromiseResolverBreakpoint) {
      super(selector, arguments, originalSender, resolver, onReceive,
          triggerMessageReceiverBreakpoint, triggerPromiseResolverBreakpoint);
    }
  }

  /** The callback message to be send after a promise is resolved. */
  public abstract static class AbstractPromiseCallbackMessage extends PromiseMessage {
    /**
     * The promise on which this callback is registered on.
     */
    @CompilationFinal protected SPromise promise;

    protected AbstractPromiseCallbackMessage(final Actor owner, final SBlock callback,
        final SResolver resolver, final RootCallTarget onReceive,
        final boolean triggerMessageReceiverBreakpoint,
        final boolean triggerPromiseResolverBreakpoint, final SPromise promiseRegisteredOn) {
      super(new Object[] {callback, null}, owner, resolver, onReceive,
          triggerMessageReceiverBreakpoint, triggerPromiseResolverBreakpoint);
      this.promise = promiseRegisteredOn;
      if (VmSettings.SNAPSHOTS_ENABLED && !VmSettings.REPLAY) {
        this.messageId = ActorProcessingThread.currentThread().getSnapshotId();
      }
    }

    @Override
    public void resolve(final Object rcvr, final Actor target, final Actor sendingActor) {
      setPromiseValue(rcvr, sendingActor);
    }

    /**
     * The value the promise was resolved to on which this callback is
     * registered on.
     *
     * @param resolvingActor - the owner of the value, the promise was resolved to.
     */
    private void setPromiseValue(final Object value, final Actor resolvingActor) {
      args[1] = originalSender.wrapForUse(value, resolvingActor, null);
      if (VmSettings.SNAPSHOTS_ENABLED && !VmSettings.REPLAY) {
        this.messageId = ActorProcessingThread.currentThread().getSnapshotId();
      }
    }

    @Override
    public SSymbol getSelector() {
      return ((SBlock) args[0]).getMethod().getSignature();
    }

    @Override
    public Actor getTarget() {
      assert originalSender != null;
      return originalSender;
    }

    @Override
    public String toString() {
      return "PCallbackMsg(" + Arrays.toString(args) + ")";
    }

    @Override
    public SPromise getPromise() {
      return promise;
    }

    /**
     * Used for Fixup in deserialization.
     */
    @Override
    public final void setPromise(final SPromise promise) {
      assert VmSettings.SNAPSHOTS_ENABLED;
      assert promise != null && this.promise == null;
      this.promise = promise;
    }

    @Override
    public boolean isDelivered() {
      return originalSender != null;
    }
  }

  public static final class PromiseCallbackMessage extends AbstractPromiseCallbackMessage {
    public PromiseCallbackMessage(final Actor owner, final SBlock callback,
        final SResolver resolver, final RootCallTarget onReceive,
        final boolean triggerMessageReceiverBreakpoint,
        final boolean triggerPromiseResolverBreakpoint, final SPromise promiseRegisteredOn) {
      super(owner, callback, resolver, onReceive, triggerMessageReceiverBreakpoint,
          triggerPromiseResolverBreakpoint, promiseRegisteredOn);
    }
  }

  public final void execute() {
    Object rcvrObj = args[0];
    assert rcvrObj != null;

    assert !(rcvrObj instanceof SFarReference);
    assert !(rcvrObj instanceof SPromise);

    assert onReceive.getRootNode() instanceof ReceivedMessage
        || onReceive.getRootNode() instanceof ReceivedCallback;
    onReceive.call(this);
  }

  public static Actor getActorCurrentMessageIsExecutionOn() {
    Thread t = Thread.currentThread();
    return ((ActorProcessingThread) t).currentlyExecutingActor;
  }

  public static EventualMessage getCurrentExecutingMessage() {
    Thread t = Thread.currentThread();
    return ((ActorProcessingThread) t).currentMessage;
  }

  public Object[] getArgs() {
    return args;
  }

  /**
   * Indicates that execution should stop and yield to the debugger,
   * before the message is processed.
   */
  public boolean getHaltOnReceive() {
    return haltOnReceive;
  }

  /**
   * Sets the flag for the message receiver breakpoint.
   */
  void enableHaltOnReceive() {
    haltOnReceive = true;
  }

  /**
   * Indicates that execution should stop and yield to the debugger,
   * before the computed value is used to resolve the promise.
   */
  boolean getHaltOnResolver() {
    return haltOnResolver;
  }

  /**
   * Indicates that a resolution breakpoint is set.
   */
  boolean getHaltOnResolution() {
    if (resolver == null) {
      return false;
    }
    return resolver.getPromise().getHaltOnResolution();
  }

  /**
   * Indicates that the message is a promise message and a resolution breakpoint
   * is set.
   */
  public abstract boolean getHaltOnPromiseMessageResolution();
}
