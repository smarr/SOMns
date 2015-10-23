package som.interpreter.actors;

import som.interpreter.SArguments;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;


public final class ReceivedMessage extends RootNode {

  @Child protected AbstractMessageSendNode onReceive;
  @Child protected ResolvePromiseNode resolve;
  private final SSymbol selector;

  public ReceivedMessage(final AbstractMessageSendNode onRecieve, final SSymbol selector) {
    super(SomLanguage.class, null, null);
    this.onReceive = onRecieve;
    this.selector  = selector;
    this.resolve = ResolvePromiseNodeFactory.create(null, null);
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    EventualMessage msg = (EventualMessage) SArguments.rcvr(frame);

    Object result = onReceive.doPreEvaluated(frame, msg.args);

    if (msg.resolver != null) {
      resolve.executeEvaluated(msg.resolver, result);
    }
    return null;
  }

  @Override
  public String toString() {
    return "RcvdMsg(" + selector.toString() + ")";
  }

  public static final class ReceivedCallback extends RootNode {
    @Child protected DirectCallNode onReceive;
    @Child protected ResolvePromiseNode resolve;

    public ReceivedCallback(final RootCallTarget onReceive) {
      super(SomLanguage.class, null, null);
      this.onReceive = Truffle.getRuntime().createDirectCallNode(onReceive);
      this.resolve = ResolvePromiseNodeFactory.create(null, null);
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      EventualMessage msg = (EventualMessage) SArguments.rcvr(frame);

      Object result = onReceive.call(frame, msg.args);

      if (msg.resolver != null) {
        resolve.executeEvaluated(msg.resolver, result);
      }
      return null;
    }
  }
}
