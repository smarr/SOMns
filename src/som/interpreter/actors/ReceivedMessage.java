package som.interpreter.actors;

import java.util.concurrent.CompletableFuture;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;

import som.interpreter.SArguments;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;


public class ReceivedMessage extends ReceivedRootNode {

  @Child protected AbstractMessageSendNode onReceive;

  private final SSymbol selector;

  public ReceivedMessage(final AbstractMessageSendNode onRecieve, final SSymbol selector) {
    super(SomLanguage.class, onRecieve.getSourceSection(), null);
    this.onReceive = onRecieve;
    this.selector  = selector;
    assert onRecieve.getSourceSection() != null;
  }

  @Override
  public Object execute(final VirtualFrame frame) {
    EventualMessage msg = (EventualMessage) SArguments.rcvr(frame);

    Object result = onReceive.doPreEvaluated(frame, msg.args);

    resolvePromise(msg.resolver, result);
    return null;
  }

  @Override
  public String toString() {
    return "RcvdMsg(" + selector.toString() + ")";
  }

  public static final class ReceivedMessageForVMMain extends ReceivedMessage {
    private final CompletableFuture<Object> future;

    public ReceivedMessageForVMMain(final AbstractMessageSendNode onReceive,
        final SSymbol selector, final CompletableFuture<Object> future) {
      super(onReceive, selector);
      this.future = future;
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      EventualMessage msg = (EventualMessage) SArguments.rcvr(frame);

      Object result = onReceive.doPreEvaluated(frame, msg.args);
      future.complete(result);
      return result;
    }
  }

  public static final class ReceivedCallback extends ReceivedRootNode {
    @Child protected DirectCallNode onReceive;

    public ReceivedCallback(final RootCallTarget onReceive) {
      super(SomLanguage.class, onReceive.getRootNode().getSourceSection(), null);
      this.onReceive = Truffle.getRuntime().createDirectCallNode(onReceive);
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      EventualMessage msg = (EventualMessage) SArguments.rcvr(frame);

      Object result = onReceive.call(frame, msg.args);

      resolvePromise(msg.resolver, result);
      return null;
    }
  }
}
