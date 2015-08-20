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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;


@NodeChild(value = "arguments", type = InternalObjectArrayNode.class)
public abstract class EventualSendNode extends ExpressionNode {

  protected final SSymbol selector;
  protected final RootCallTarget onReceive;

  private static final MixinDefinitionId fakeId = new MixinDefinitionId(Symbols.symbolFor("--fake--"));

  public EventualSendNode(final SSymbol selector,
      final int numArgs, final SourceSection source) {
    super(source);
    this.selector = selector;

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

    onReceive = m.createCallTarget();
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

  @Specialization(guards = {"markVmHasSendMessage()", "isFarRefRcvr(args)"})
  public final SPromise toFarRef(final Object[] args) {
    SFarReference rcvr = (SFarReference) args[0];
    return rcvr.eventualSend(EventualMessage.getActorCurrentMessageIsExecutionOn(),
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
