package tools.replay.nodes;

import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.interpreter.actors.SPromise.STracingPromise;
import tools.concurrency.TracingActors.TracingActor;
import tools.replay.TraceRecord;
import tools.replay.actors.ExternalEventualMessage.ExternalDirectMessage;
import tools.replay.actors.ExternalEventualMessage.ExternalPromiseCallbackMessage;
import tools.replay.actors.ExternalEventualMessage.ExternalPromiseSendMessage;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;
import tools.replay.nodes.RecordEventNodes.RecordThreeEvent;
import tools.replay.nodes.RecordEventNodes.RecordTwoEvent;


public abstract class TraceMessageNode extends TraceNode {

  @Child TraceContextNode           tracer      = TraceContextNodeGen.create();
  @Child protected RecordOneEvent   recDMsg     =
      new RecordOneEvent(TraceRecord.MESSAGE);
  @Child protected RecordTwoEvent   recEDMsg    = new RecordTwoEvent(
      (byte) (TraceRecord.EXTERNAL_BIT | TraceRecord.MESSAGE.value));
  @Child protected RecordTwoEvent   recPromMsg  =
      new RecordTwoEvent(TraceRecord.PROMISE_MESSAGE.value);
  @Child protected RecordThreeEvent recEPromMsg = new RecordThreeEvent(
      (byte) (TraceRecord.EXTERNAL_BIT | TraceRecord.PROMISE_MESSAGE.value));

  public abstract void execute(EventualMessage msg);

  @Specialization
  public void trace(final DirectMessage msg) {
    recDMsg.record(((TracingActor) msg.getSender()).getId());
  }

  @Specialization
  public void trace(final ExternalDirectMessage msg) {
    long edata = (msg.getMethod() << Integer.BYTES) | msg.getDataId();
    recEDMsg.record(((TracingActor) msg.getSender()).getId(), edata);
  }

  @Specialization
  public void trace(final PromiseCallbackMessage msg) {
    recPromMsg.record(((TracingActor) msg.getSender()).getId(),
        ((STracingPromise) msg.getPromise()).getResolvingActor());
  }

  @Specialization
  public void trace(final PromiseSendMessage msg) {
    recPromMsg.record(((TracingActor) msg.getSender()).getId(),
        ((STracingPromise) msg.getPromise()).getResolvingActor());
  }

  @Specialization
  public void trace(final ExternalPromiseCallbackMessage msg) {
    long edata = (msg.getMethod() << Integer.BYTES) | msg.getDataId();
    recEPromMsg.record(((TracingActor) msg.getSender()).getId(),
        ((STracingPromise) msg.getPromise()).getResolvingActor(), edata);
  }

  @Specialization
  public void trace(final ExternalPromiseSendMessage msg) {
    long edata = (msg.getMethod() << Integer.BYTES) | msg.getDataId();
    recEPromMsg.record(((TracingActor) msg.getSender()).getId(),
        ((STracingPromise) msg.getPromise()).getResolvingActor(), edata);
  }
}
