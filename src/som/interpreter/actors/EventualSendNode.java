package som.interpreter.actors;

import som.VM;
import som.compiler.MethodBuilder;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.Method;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ArgumentReadNode.LocalSelfReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.InternalObjectArrayNode;
import som.interpreter.nodes.MessageSendNode;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vm.Symbols;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;


@NodeChild(value = "arguments", type = InternalObjectArrayNode.class)
public abstract class EventualSendNode extends ExpressionNode {

  protected final SSymbol selector;
  protected final RootCallTarget onReceive;
  @Children protected final WrapReferenceNode[] wrapArgs;

  private static final MixinDefinitionId fakeId = new MixinDefinitionId(Symbols.symbolFor("--fake--"));

  public EventualSendNode(final SSymbol selector,
      final int numArgs, final SourceSection source) {
    super(source);
    this.selector = selector;

    onReceive = createOnReceiveCallTarget(selector, numArgs, source);
    wrapArgs  = createArgWrapper(numArgs);
  }

  private static RootCallTarget createOnReceiveCallTarget(final SSymbol selector,
      final int numArgs, final SourceSection source) {
    MethodBuilder eventualInvoke = new MethodBuilder(true);
    ExpressionNode[] args = new ExpressionNode[numArgs];
    args[0] = new LocalSelfReadNode(fakeId, null);
    for (int i = 1; i < numArgs; i++) {
      args[i] = new LocalArgumentReadNode(i, null);
    }
    AbstractMessageSendNode invoke = MessageSendNode.createMessageSend(selector, args, source);

    Method m = new Method(source, invoke,
        eventualInvoke.getCurrentMethodScope(),
        (ExpressionNode) invoke.deepCopy());

    return m.createCallTarget();
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

  @ExplodeLoop
  @Specialization(guards = {"markVmHasSendMessage()", "isFarRefRcvr(args)"})
  public final SPromise toFarRef(final Object[] args) {
    CompilerAsserts.compilationConstant(args.length);

    SFarReference rcvr = (SFarReference) args[0];
    Actor target = rcvr.getActor();
    Actor owner  = EventualMessage.getActorCurrentMessageIsExecutionOn();

    for (int i = 0; i < args.length; i++) {
      args[i] = wrapArgs[i].execute(args[i], target, owner);
    }

    assert !(args[0] instanceof SFarReference) : "This should not happen for this specialization, but it is handled in determineTargetAndWrapArguments(.)";
    assert !(args[0] instanceof SPromise) : "Should not happen either, but just to be sure";

    return target.eventualSend(owner,
        selector, args, onReceive);
  }

  @Specialization(guards = "isPromiseRcvr(args)")
  public final SPromise toPromise(final Object[] args) {
    SPromise rcvr = (SPromise) args[0];
    return rcvr.whenResolved(selector, args, onReceive);
  }

  @Specialization(guards = {"!isFarRefRcvr(args)", "!isPromiseRcvr(args)"})
  public final SPromise toNearRef(final Object[] args) {
    Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();
    return current.eventualSend(current, selector, args, onReceive);
  }

  @Override
  public String toString() {
    return "EventSend[" + selector.toString() + "]";
  }
}
