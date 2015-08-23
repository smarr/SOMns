package som.interpreter.actors;

import som.interpreter.SArguments;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;


public final class ReceivedMessage extends RootNode {

  @Child protected AbstractMessageSendNode onReceive;
  private final SSymbol selector;

  public ReceivedMessage(final AbstractMessageSendNode onRecieve, final SSymbol selector) {
    this.onReceive = onRecieve;
    this.selector  = selector;
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    EventualMessage msg = (EventualMessage) SArguments.rcvr(frame);

    Object result = onReceive.doPreEvaluated(frame, msg.args);

    if (msg.resolver != null) {
      msg.resolver.resolve(result);
    }
    return null;
  }

  @Override
  public String toString() {
    return "RcvdMsg(" + selector.toString() + ")";
  }

  public static final class ReceivedCallback extends RootNode {
    @Child protected DirectCallNode onReceive;

    public ReceivedCallback(final RootCallTarget onReceive) {
      this.onReceive = Truffle.getRuntime().createDirectCallNode(onReceive);
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      EventualMessage msg = (EventualMessage) SArguments.rcvr(frame);

      Object result = onReceive.call(frame, msg.args);

      if (msg.resolver != null) {
        msg.resolver.resolve(result);
      }
      return null;
    }
  }
}
