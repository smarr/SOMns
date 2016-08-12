package som.interpreter.actors;

import java.util.concurrent.CompletableFuture;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.VmSettings;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.interpreter.actors.EventualSendNodeFactory.BreakpointNodeGen;
import som.interpreter.actors.EventualSendNodeFactory.SendNodeGen;
import som.interpreter.actors.ReceivedMessage.ReceivedMessageForVMMain;
import som.interpreter.actors.RegisterOnPromiseNode.RegisterWhenResolved;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.InternalObjectArrayNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.interpreter.nodes.SOMNode;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vm.constants.Nil;
import som.vmobjects.SSymbol;
import tools.actors.Tags.EventualMessageSend;
import tools.debugger.session.Breakpoints;
import tools.debugger.session.Breakpoints.BreakpointInfo;
import tools.debugger.session.Breakpoints.SectionBreakpoint;


@Instrumentable(factory = EventualSendNodeWrapper.class)
public class EventualSendNode extends ExprWithTagsNode {
  @Child protected InternalObjectArrayNode arguments;
  @Child protected SendNode send;

  public EventualSendNode(final SSymbol selector, final int numArgs,
      final InternalObjectArrayNode arguments, final SourceSection source,
      final SourceSection sendOperator) {
    super(source);
    this.arguments = arguments;
    this.send = SendNodeGen.create(selector, createArgWrapper(numArgs),
        createOnReceiveCallTarget(selector, numArgs, source), sendOperator);
  }

  /**
   * Use for wrapping node only.
   */
  protected EventualSendNode(final EventualSendNode wrappedNode) {
    super((SourceSection) null);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    Object[] args = arguments.executeObjectArray(frame);
    return send.execute(frame, args);
  }

  private static RootCallTarget createOnReceiveCallTarget(final SSymbol selector,
      final int numArgs, final SourceSection source) {

    AbstractMessageSendNode invoke = MessageSendNode.createGeneric(selector, null, source);
    ReceivedMessage receivedMsg = new ReceivedMessage(invoke, selector);

    return Truffle.getRuntime().createCallTarget(receivedMsg);
  }

  public static RootCallTarget createOnReceiveCallTargetForVMMain(final SSymbol selector,
      final int numArgs, final SourceSection source, final CompletableFuture<Object> future) {

    AbstractMessageSendNode invoke = MessageSendNode.createGeneric(selector, null, source);
    ReceivedMessage receivedMsg = new ReceivedMessageForVMMain(invoke, selector, future);

    return Truffle.getRuntime().createCallTarget(receivedMsg);
  }

  private static WrapReferenceNode[] createArgWrapper(final int numArgs) {
    WrapReferenceNode[] wrapper = new WrapReferenceNode[numArgs];
    for (int i = 0; i < numArgs; i++) {
      wrapper[i] = WrapReferenceNodeGen.create();
    }
    return wrapper;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return isParentResultUsed(this, this);
  }

  public static boolean isParentResultUsed(final Node current, final Node child) {
    Node parent = SOMNode.getParentIgnoringWrapper(current);

    assert parent != null;
    if (parent instanceof ExpressionNode) {
      return ((ExpressionNode) parent).isResultUsed((ExpressionNode) child);
    }
    return true;
  }

  @Instrumentable(factory = SendNodeWrapper.class)
  public abstract static class SendNode extends Node {
    protected final SSymbol selector;
    @Children protected final WrapReferenceNode[] wrapArgs;
    protected final RootCallTarget onReceive;

    protected final SourceSection source;

    protected final AbstractBreakpointNode breakpoint;

    protected SendNode(final SSymbol selector, final WrapReferenceNode[] wrapArgs,
        final RootCallTarget onReceive, final SourceSection source) {
      this.selector = selector;
      this.wrapArgs = wrapArgs;
      this.onReceive = onReceive;
      this.source = source;

      if (selector == null) {
        // this node is going to be used as a wrapper node
        this.breakpoint = null;
      } else if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        this.breakpoint = insert(BreakpointNodeGen.create(source));
      } else {
        this.breakpoint = insert(new DisabledBreakpointNode());
      }
    }

    /**
     * Use for wrapping node only.
     */
    protected SendNode(final SendNode wrappedNode) {
      this(null, null, null, null);
    }

    public abstract Object execute(VirtualFrame frame, Object[] args);

    @Override
    public SourceSection getSourceSection() {
      return source;
    }

    protected final boolean isResultUsed() {
      Node parent = SOMNode.getParentIgnoringWrapper(this);
      assert parent instanceof EventualSendNode;
      return isParentResultUsed(parent, parent);
    }

    protected static final boolean isFarRefRcvr(final Object[] args) {
      return args[0] instanceof SFarReference;
    }

    protected static final boolean isPromiseRcvr(final Object[] args) {
      return args[0] instanceof SPromise;
    }

    @ExplodeLoop
    protected void sendDirectMessage(final Object[] args, final Actor owner,
        final SResolver resolver) {
      CompilerAsserts.compilationConstant(args.length);

      SFarReference rcvr = (SFarReference) args[0];
      Actor target = rcvr.getActor();

      for (int i = 0; i < args.length; i++) {
        args[i] = wrapArgs[i].execute(args[i], target, owner);
      }

      assert !(args[0] instanceof SFarReference) : "This should not happen for this specialization, but it is handled in determineTargetAndWrapArguments(.)";
      assert !(args[0] instanceof SPromise) : "Should not happen either, but just to be sure";

      DirectMessage msg = new DirectMessage(
          EventualMessage.getCurrentExecutingMessage(), target, selector, args,
          owner, resolver, onReceive, breakpoint.executeCheckIsSetAndEnabled());
      target.send(msg);
    }

    protected void sendPromiseMessage(final Object[] args, final SPromise rcvr,
        final SResolver resolver, final RegisterWhenResolved registerNode) {
      assert rcvr.getOwner() == EventualMessage.getActorCurrentMessageIsExecutionOn() : "think this should be true because the promise is an Object and owned by this specific actor";

      PromiseSendMessage msg = new PromiseSendMessage(
          EventualMessage.getCurrentExecutingMessage(), selector, args,
          rcvr.getOwner(), resolver, onReceive, breakpoint.executeCheckIsSetAndEnabled());
      registerNode.register(rcvr, msg, rcvr.getOwner());
    }

    protected static RegisterWhenResolved createRegisterNode() {
      return new RegisterWhenResolved();
    }

    @Override
    public String toString() {
      return "EventSend[" + selector.toString() + "]";
    }

    @Specialization(guards = {"isResultUsed()", "isFarRefRcvr(args)"})
    public final SPromise toFarRefWithResultPromise(final Object[] args) {
      Actor owner = EventualMessage.getActorCurrentMessageIsExecutionOn();

      SPromise  result   = SPromise.createPromise(owner);
      SResolver resolver = SPromise.createResolver(result, "eventualSend:", selector);

      sendDirectMessage(args, owner, resolver);

      return result;
    }

    @Specialization(guards = {"isResultUsed()", "isPromiseRcvr(args)"})
    public final SPromise toPromiseWithResultPromise(final Object[] args,
        @Cached("createRegisterNode()") final RegisterWhenResolved registerNode) {
      SPromise rcvr = (SPromise) args[0];
      SPromise  promise  = SPromise.createPromise(EventualMessage.getActorCurrentMessageIsExecutionOn());
      SResolver resolver = SPromise.createResolver(promise, "eventualSendToPromise:", selector);

      sendPromiseMessage(args, rcvr, resolver, registerNode);
      return promise;
    }

    @Specialization(guards = {"isResultUsed()", "!isFarRefRcvr(args)", "!isPromiseRcvr(args)"})
    public final SPromise toNearRefWithResultPromise(final Object[] args) {
      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
      SPromise  result   = SPromise.createPromise(current);
      SResolver resolver = SPromise.createResolver(result, "eventualSend:", selector);

      DirectMessage msg = new DirectMessage(EventualMessage.getCurrentExecutingMessage(),
          current, selector, args, current,
          resolver, onReceive, breakpoint.executeCheckIsSetAndEnabled());
      current.send(msg);

      return result;
    }

    @Specialization(guards = {"!isResultUsed()", "isFarRefRcvr(args)"})
    public final Object toFarRefWithoutResultPromise(final Object[] args) {
      Actor owner = EventualMessage.getActorCurrentMessageIsExecutionOn();

      sendDirectMessage(args, owner, null);

      return Nil.nilObject;
    }

    @Specialization(guards = {"!isResultUsed()", "isPromiseRcvr(args)"})
    public final Object toPromiseWithoutResultPromise(final Object[] args,
        @Cached("createRegisterNode()") final RegisterWhenResolved registerNode) {
      sendPromiseMessage(args, (SPromise) args[0], null, registerNode);
      return Nil.nilObject;
    }

    @Specialization(guards = {"!isResultUsed()", "!isFarRefRcvr(args)", "!isPromiseRcvr(args)"})
    public final Object toNearRefWithoutResultPromise(final Object[] args) {
      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();

      DirectMessage msg = new DirectMessage(EventualMessage.getCurrentExecutingMessage(),
          current, selector, args, current,
          null, onReceive, breakpoint.executeCheckIsSetAndEnabled());
      current.send(msg);
      return Nil.nilObject;
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == EventualMessageSend.class) {
        return true;
      } else if (tag == StatementTag.class) {
        return true;
      }
      return super.isTaggedWith(tag);
    }
  }

  protected abstract static class AbstractBreakpointNode extends Node {
    public abstract boolean executeCheckIsSetAndEnabled();
  }

  protected abstract static class BreakpointNode extends AbstractBreakpointNode {
    private final Breakpoints breakpoints;
    private final SectionBreakpoint section;

    protected BreakpointNode(final SourceSection sourceSection) {
      this.section = new SectionBreakpoint(sourceSection);
      this.breakpoints = VM.getWebDebugger().getBreakpoints();
    }

    /** Only to be used by the DisabledBreakpointNode. */
    protected BreakpointNode() {
      this.section     = null;
      this.breakpoints = null;
    }


    protected final BreakpointInfo getBreakpointStatus() {
      return breakpoints.hasReceiverBreakpoint(section);
    }

    @Specialization(assumptions = "info.receiverBreakpointVersion", guards = "info.noBreakpoint()")
    public final boolean noBreakpoint(@Cached("getBreakpointStatus()") final BreakpointInfo info) {
      return false;
    }

    @Specialization(assumptions = {"info.receiverBreakpointVersion", "bpUnchanged"},
        guards = {"info.hasBreakpoint()", "info.breakpoint.isDisabled()"})
    public final boolean breakpointDisabled(@Cached("getBreakpointStatus()") final BreakpointInfo info,
        @Cached("info.breakpoint.getAssumption()") final Assumption bpUnchanged) {
      return false;
    }

    @Specialization(assumptions = {"info.receiverBreakpointVersion", "bpUnchanged"},
        guards = {"info.hasBreakpoint()", "info.breakpoint.isEnabled()"})
    public final boolean breakpointEnabled(@Cached("getBreakpointStatus()") final BreakpointInfo info,
        @Cached("info.breakpoint.getAssumption()") final Assumption bpUnchanged) {
      return true;
    }
  }

  private static final class DisabledBreakpointNode extends AbstractBreakpointNode {
    @Override
    public boolean executeCheckIsSetAndEnabled() {
      return false;
    }
  }
}
