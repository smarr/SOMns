package som.interpreter.actors;

import som.VM;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.interpreter.actors.RegisterOnPromiseNode.RegisterWhenResolved;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.InternalObjectArrayNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vm.constants.Nil;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;


@NodeChild(value = "arguments", type = InternalObjectArrayNode.class)
public abstract class EventualSendNode extends ExpressionNode {

  protected final SSymbol selector;
  protected final RootCallTarget onReceive;
  @Children protected final WrapReferenceNode[] wrapArgs;

  public EventualSendNode(final SSymbol selector,
      final int numArgs, final SourceSection source) {
    super(source);
    this.selector = selector;

    onReceive = createOnReceiveCallTarget(selector, numArgs, source);
    wrapArgs  = createArgWrapper(numArgs);
  }

  private static RootCallTarget createOnReceiveCallTarget(final SSymbol selector,
      final int numArgs, final SourceSection source) {

    AbstractMessageSendNode invoke = MessageSendNode.createGeneric(selector, null, source);
    ReceivedMessage receivedMsg = new ReceivedMessage(invoke, selector);

    return Truffle.getRuntime().createCallTarget(receivedMsg);
  }

  private static WrapReferenceNode[] createArgWrapper(final int numArgs) {
    WrapReferenceNode[] wrapper = new WrapReferenceNode[numArgs];
    for (int i = 0; i < numArgs; i++) {
      wrapper[i] = WrapReferenceNodeGen.create();
    }
    return wrapper;
  }

  protected static final boolean markVmHasSendMessage() {
    // no arguments, should be compiled to assertion and only executed once
    if (CompilerDirectives.inInterpreter()) {
      VM.hasSendMessages();
    }
    return true;
  }

  protected static final boolean isFarRefRcvr(final Object[] args) {
    return args[0] instanceof SFarReference;
  }

  protected static final boolean isPromiseRcvr(final Object[] args) {
    return args[0] instanceof SPromise;
  }

  protected final boolean isResultUsed() {
    return isResultUsed(null);
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    Node parent = getParent();
    assert parent != null;
    if (parent instanceof ExpressionNode) {
      return ((ExpressionNode) parent).isResultUsed(this);
    }
    return true;
  }

  @Specialization(guards = {"isResultUsed()", "markVmHasSendMessage()", "isFarRefRcvr(args)"})
  public final SPromise toFarRefWithResultPromise(final Object[] args) {
    Actor owner = EventualMessage.getActorCurrentMessageIsExecutionOn();

    SPromise  result   = SPromise.createPromise(owner);
    SResolver resolver = SPromise.createResolver(result, "eventualSend:", selector);

    sendDirectMessage(args, owner, resolver);

    return result;
  }

  @Specialization(guards = {"!isResultUsed()", "markVmHasSendMessage()", "isFarRefRcvr(args)"})
  public final Object toFarRefWithoutResultPromise(final Object[] args) {
    Actor owner = EventualMessage.getActorCurrentMessageIsExecutionOn();

    sendDirectMessage(args, owner, null);

    return Nil.nilObject;
  }

  @ExplodeLoop
  private void sendDirectMessage(final Object[] args, final Actor owner,
      final SResolver resolver) {
    CompilerAsserts.compilationConstant(args.length);

    SFarReference rcvr = (SFarReference) args[0];
    Actor target = rcvr.getActor();

    for (int i = 0; i < args.length; i++) {
      args[i] = wrapArgs[i].execute(args[i], target, owner);
    }

    assert !(args[0] instanceof SFarReference) : "This should not happen for this specialization, but it is handled in determineTargetAndWrapArguments(.)";
    assert !(args[0] instanceof SPromise) : "Should not happen either, but just to be sure";


    DirectMessage msg = new DirectMessage(target, selector, args, owner,
        resolver, onReceive);
    target.send(msg);
  }

  protected static RegisterWhenResolved createRegisterNode() {
    return new RegisterWhenResolved();
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

  @Specialization(guards = {"!isResultUsed()", "isPromiseRcvr(args)"})
  public final Object toPromiseWithoutResultPromise(final Object[] args,
      @Cached("createRegisterNode()") final RegisterWhenResolved registerNode) {
    sendPromiseMessage(args, (SPromise) args[0], null, registerNode);
    return Nil.nilObject;
  }

  private void sendPromiseMessage(final Object[] args, final SPromise rcvr,
      final SResolver resolver, final RegisterWhenResolved register) {
    assert rcvr.getOwner() == EventualMessage.getActorCurrentMessageIsExecutionOn() : "think this should be true because the promise is an Object and owned by this specific actor";
    PromiseSendMessage msg = new PromiseSendMessage(selector, args, rcvr.getOwner(), resolver, onReceive);

    register.register(rcvr, msg, rcvr.getOwner());
  }

  @Specialization(guards = {"isResultUsed()", "!isFarRefRcvr(args)", "!isPromiseRcvr(args)"})
  public final SPromise toNearRefWithResultPromise(final Object[] args) {
    Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
    SPromise  result   = SPromise.createPromise(current);
    SResolver resolver = SPromise.createResolver(result, "eventualSend:", selector);

    DirectMessage msg = new DirectMessage(current, selector, args, current,
        resolver, onReceive);
    current.send(msg);

    return result;
  }

  @Specialization(guards = {"!isResultUsed()", "!isFarRefRcvr(args)", "!isPromiseRcvr(args)"})
  public final Object toNearRefWithoutResultPromise(final Object[] args) {
    Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
    DirectMessage msg = new DirectMessage(current, selector, args, current,
        null, onReceive);
    current.send(msg);
    return Nil.nilObject;
  }

  @Override
  public String toString() {
    return "EventSend[" + selector.toString() + "]";
  }
}
