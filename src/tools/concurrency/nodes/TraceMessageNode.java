package tools.concurrency.nodes;

import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.DirectMessage;
import som.interpreter.actors.EventualMessage.ExternalDirectMessage;
import som.interpreter.actors.EventualMessage.ExternalPromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.ExternalPromiseSendMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.EventualMessage.PromiseSendMessage;
import som.interpreter.actors.SPromise.STracingPromise;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;
import tools.concurrency.ByteBuffer;
import tools.concurrency.TracingActors.TracingActor;


public abstract class TraceMessageNode extends TraceNode {

  private static final int DIRECT_MSG_SIZE      = 5;
  private static final int PROMISE_MSG_SIZE     = 9;
  private static final int EXT_DIRECT_MSG_SIZE  = 11;
  private static final int EXT_PROMISE_MSG_SIZE = 15;

  @Child TraceActorContextNode    tracer = new TraceActorContextNode();
  @Child protected RecordIdNode   id     = RecordIdNodeGen.create();
  @Child protected RecordIdIdNode idid   = RecordIdIdNodeGen.create();

  public abstract void execute(EventualMessage msg);

  private ByteBuffer getStorage(final int entrySize) {
    ActorTraceBuffer buffer = getCurrentBuffer();
    return buffer.ensureSufficientSpace(entrySize, tracer);
  }

  @Specialization
  public void trace(final DirectMessage msg) {
    ByteBuffer storage = getStorage(DIRECT_MSG_SIZE);

    int pos = storage.position();

    int idLen = id.execute(storage, pos + 1, ((TracingActor) msg.getSender()).getActorId());
    int idBit = (idLen - 1) << 4;

    storage.put((byte) (ActorExecutionTrace.MESSAGE | idBit));
    storage.position(pos + idLen + 1);
  }

  @Specialization
  public void trace(final ExternalDirectMessage msg) {
    ByteBuffer storage = getStorage(EXT_DIRECT_MSG_SIZE);

    int pos = storage.position();

    int idLen = id.execute(storage, pos + 1,
        ((TracingActor) msg.getSender()).getActorId());
    int idBit = (idLen - 1) << 4;

    storage.putByteAt(pos,
        (byte) (ActorExecutionTrace.EXTERNAL_BIT | ActorExecutionTrace.MESSAGE | idBit));

    pos += idLen;

    storage.putShortAt(pos, msg.getMethod());
    pos += 2;
    storage.putIntAt(pos, msg.getDataId());
    pos += 4;

    storage.position(pos);
  }

  @Specialization
  public void trace(final PromiseCallbackMessage msg) {
    tracePromiseMsg(msg);
  }

  @Specialization
  public void trace(final PromiseSendMessage msg) {
    tracePromiseMsg(msg);
  }

  private void tracePromiseMsg(final PromiseMessage msg) {
    ByteBuffer storage = getStorage(PROMISE_MSG_SIZE);

    int pos = storage.position();

    int idLen = idid.execute(storage, pos + 1,
        ((TracingActor) msg.getSender()).getActorId(),
        ((STracingPromise) msg.getPromise()).getResolvingActor());
    int idBit = (idLen - 1) << 4;

    storage.put((byte) (ActorExecutionTrace.PROMISE_MESSAGE | idBit));
    storage.position(pos + idLen + idLen + 1);
  }

  @Specialization
  public void trace(final ExternalPromiseCallbackMessage msg) {
    traceExternalPromiseMsg(msg, msg.getMethod(), msg.getDataId());
  }

  @Specialization
  public void trace(final ExternalPromiseSendMessage msg) {
    traceExternalPromiseMsg(msg, msg.getMethod(), msg.getDataId());
  }

  private void traceExternalPromiseMsg(final PromiseMessage msg, final short method,
      final int dataId) {
    ByteBuffer storage = getStorage(EXT_PROMISE_MSG_SIZE);

    int pos = storage.position();

    int idLen = idid.execute(storage, pos + 1,
        ((TracingActor) msg.getSender()).getActorId(),
        ((STracingPromise) msg.getPromise()).getResolvingActor());
    int idBit = (idLen - 1) << 4;

    storage.putByteAt(pos,
        (byte) (ActorExecutionTrace.EXTERNAL_BIT | ActorExecutionTrace.PROMISE_MESSAGE
            | idBit));

    pos += idLen + idLen;

    storage.putShortAt(pos, method);
    pos += 2;
    storage.putIntAt(pos, dataId);
    pos += 4;

    storage.position(pos);
  }
}
