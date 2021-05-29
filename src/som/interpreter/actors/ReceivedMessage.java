package som.interpreter.actors;

import java.util.concurrent.CompletableFuture;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SomException;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.MessageSendNode.AbstractMessageSendNode;
import som.vmobjects.SSymbol;
import som.interpreter.Invokable;
import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import tools.debugger.asyncstacktraces.ShadowStackEntry;


public class ReceivedMessage extends ReceivedRootNode {

  @Child protected AbstractMessageSendNode onReceive;

  private final SSymbol selector;

  public ReceivedMessage(final AbstractMessageSendNode onReceive,
      final SSymbol selector, final SomLanguage lang) {
    super(lang, onReceive.getSourceSection(), null);
    this.onReceive = onReceive;
    this.selector = selector;
    assert onReceive.getSourceSection() != null;
  }

  @Override
  public String getName() {
    return selector.toString();
  }

  @Override
  protected Object executeBody(final VirtualFrame frame, final EventualMessage msg,
      final boolean haltOnResolver, final boolean haltOnResolution) {
    ShadowStackEntry resolutionEntry = null;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      ShadowStackEntry entry = SArguments.getShadowStackEntry(frame.getArguments());
      assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || entry != null;

      ShadowStackEntry.EntryForPromiseResolution.ResolutionLocation onReceiveLocation = ShadowStackEntry.EntryForPromiseResolution.ResolutionLocation.ON_RECEIVE_MESSAGE;
//     String resolutionValue = (msg.getTarget().getId() + " sent by actor "+ msg.getSender().getId());
      resolutionEntry =
              ShadowStackEntry.createAtPromiseResolution(entry, (ExpressionNode) onReceive, onReceiveLocation, "");
    }

    try {
      assert msg.args[msg.args.length - 1] == null
              || !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE;
      Object result = onReceive.executeEvaluated(frame, msg.args);
      resolvePromise(frame, msg.resolver, result,
              resolutionEntry, haltOnResolver, haltOnResolution);
    } catch (SomException exception) {
      errorPromise(frame, msg.resolver, exception.getSomObject(), resolutionEntry,
          haltOnResolver, haltOnResolution);
    }
    return null;
  }

  @Override
  public String toString() {
    return "RcvdMsg(" + selector.toString() + ")";
  }

  @Override
  public String getQualifiedName() {
    SourceSection ss = getSourceSection();
    return "RcvdMsg(" + selector.getString() + ":" + ss.getSource().getName() + ":"
        + ss.getStartLine() + ":"
        + ss.getStartColumn() + ")";
  }

  public static final class ReceivedMessageForVMMain extends ReceivedMessage {
    private final CompletableFuture<Object> future;

    public ReceivedMessageForVMMain(final AbstractMessageSendNode onReceive,
        final SSymbol selector, final CompletableFuture<Object> future,
        final SomLanguage lang) {
      super(onReceive, selector, lang);
      this.future = future;
    }

    @Override
    protected Object executeBody(final VirtualFrame frame, final EventualMessage msg,
        final boolean haltOnResolver, final boolean haltOnResolution) {
      Object result = onReceive.executeEvaluated(frame, msg.args);
      resolveFuture(result);
      return result;
    }

    @TruffleBoundary
    private void resolveFuture(final Object result) {
      future.complete(result);
    }
  }

  public static final class ReceivedCallback extends ReceivedRootNode {
    @Child protected DirectCallNode onReceive;
    private final Invokable         onReceiveMethod;

    public ReceivedCallback(final SInvokable onReceive) {
      super(SomLanguage.getLanguage(onReceive.getInvokable()),
              onReceive.getSourceSection(), null);
      this.onReceive = Truffle.getRuntime().createDirectCallNode(onReceive.getCallTarget());
      this.onReceiveMethod = onReceive.getInvokable();
    }

    @Override
    public String getName() {
      return onReceiveMethod.getName();
    }

    @Override
    protected Object executeBody(final VirtualFrame frame, final EventualMessage msg,
        final boolean haltOnResolver, final boolean haltOnResolution) {
      ShadowStackEntry resolutionEntry = null;
      if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
        ShadowStackEntry entry = SArguments.getShadowStackEntry(frame.getArguments());
        assert !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE || entry != null;
        ShadowStackEntry.EntryForPromiseResolution.ResolutionLocation onReceiveLocation =
                ShadowStackEntry.EntryForPromiseResolution.ResolutionLocation.ON_WHEN_RESOLVED_BLOCK;
//      String resolutionValue = (msg.getTarget().getId() + " send by actor "+ msg.getSender().getId());
        resolutionEntry =
                ShadowStackEntry.createAtPromiseResolution(entry, onReceiveMethod.getBodyNode(), onReceiveLocation, "");

        SArguments.setShadowStackEntry(msg.getArgs(), resolutionEntry);
      }

      try {
        Object result = onReceive.call(msg.args);
        resolvePromise(frame, msg.resolver, result, resolutionEntry, haltOnResolver,
            haltOnResolution);
      } catch (SomException exception) {
        errorPromise(frame, msg.resolver, exception.getSomObject(), resolutionEntry,
            haltOnResolver, haltOnResolution);
      }
      return null;
    }

    @Override
    public String getQualifiedName() {
      SourceSection ss = getSourceSection();
      return "RcvdCallback(" + ss.getSource().getName() + ":" + ss.getStartLine() + ":"
          + ss.getStartColumn() + ")";
    }
  }
}
