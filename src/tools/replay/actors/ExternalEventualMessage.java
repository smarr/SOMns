package tools.replay.actors;

import com.oracle.truffle.api.RootCallTarget;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage.AbstractDirectMessage;
import som.interpreter.actors.EventualMessage.AbstractPromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.AbstractPromiseSendMessage;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.vmobjects.SBlock;
import som.vmobjects.SSymbol;


public class ExternalEventualMessage {
  public static final class ExternalDirectMessage extends AbstractDirectMessage
      implements ExternalMessage {
    public ExternalDirectMessage(final Actor target, final SSymbol selector,
        final Object[] arguments,
        final Actor sender, final SResolver resolver, final RootCallTarget onReceive,
        final short method, final int dataId) {
      super(target, selector, arguments, sender, resolver, onReceive);
      this.method = method;
      this.dataId = dataId;
    }

    final short method;
    final int   dataId;

    @Override
    public short getMethod() {
      return method;
    }

    @Override
    public int getDataId() {
      return dataId;
    }
  }

  public static final class ExternalPromiseSendMessage extends AbstractPromiseSendMessage
      implements ExternalMessage {
    protected ExternalPromiseSendMessage(final SSymbol selector, final Object[] arguments,
        final Actor originalSender, final SResolver resolver, final RootCallTarget onReceive,
        final boolean triggerMessageReceiverBreakpoint,
        final boolean triggerPromiseResolverBreakpoint, final short method, final int dataId) {
      super(selector, arguments, originalSender, resolver, onReceive,
          triggerMessageReceiverBreakpoint, triggerPromiseResolverBreakpoint);
      this.dataId = dataId;
      this.method = method;
    }

    final short method;
    final int   dataId;

    @Override
    public short getMethod() {
      return method;
    }

    @Override
    public int getDataId() {
      return dataId;
    }
  }

  public static final class ExternalPromiseCallbackMessage
      extends AbstractPromiseCallbackMessage implements ExternalMessage {
    public ExternalPromiseCallbackMessage(final Actor owner, final SBlock callback,
        final SResolver resolver,
        final RootCallTarget onReceive, final boolean triggerMessageReceiverBreakpoint,
        final boolean triggerPromiseResolverBreakpoint, final SPromise promiseRegisteredOn,
        final short method, final int dataId) {
      super(owner, callback, resolver, onReceive, triggerMessageReceiverBreakpoint,
          triggerPromiseResolverBreakpoint, promiseRegisteredOn);
      this.method = method;
      this.dataId = dataId;
    }

    final short method;
    final int   dataId;

    @Override
    public short getMethod() {
      return method;
    }

    @Override
    public int getDataId() {
      return dataId;
    }
  }
}
