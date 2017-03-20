package tools.concurrency;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Queue;

import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.primitives.processes.ChannelPrimitives.TracingProcess;
import som.vm.ObjectSystem;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import tools.ObjectBuffer;
import tools.TraceData;
import tools.concurrency.ActorExecutionTrace.Events;
import tools.concurrency.ActorExecutionTrace.ParamTypes;

public class TraceBuffer {

  public static TraceBuffer create() {
    assert VmSettings.ACTOR_TRACING;
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      return new SyncedTraceBuffer();
    } else {
      return new TraceBuffer();
    }
  }

  private ByteBuffer storage;
  private long threadId;

  protected TraceBuffer() { }

  void init(final ByteBuffer storage, final long threadId) {
    this.storage = storage;
    this.threadId = threadId;
    assert storage.order() == ByteOrder.BIG_ENDIAN;
    recordThreadId();
  }

  void returnBuffer() {
    ActorExecutionTrace.returnBuffer(storage);
    storage = null;
  }

  public boolean isEmpty() {
    return storage.position() == 0;
  }

  public boolean isFull() {
    return storage.remaining() == 0;
  }

  boolean swapStorage() {
    if (storage == null || storage.position() <= Events.Thread.size) {
      return false;
    }
    ActorExecutionTrace.returnBuffer(storage);
    init(ActorExecutionTrace.getEmptyBuffer(), threadId);
    return true;
  }

  private void recordThreadId() {
    final int start = storage.position();
    assert start == 0;

    storage.put(Events.Thread.id);
    storage.putLong(threadId);
    storage.putLong(System.currentTimeMillis());

    assert storage.position() == start + Events.Thread.size;
  }

  protected boolean ensureSufficientSpace(final int requiredSpace) {
    if (storage.remaining() < requiredSpace) {
      boolean didSwap = swapStorage();
      assert didSwap;
      return didSwap;
    }
    return false;
  }

  public final void recordMainActor(final Actor mainActor,
      final ObjectSystem objectSystem) {
    SourceSection section;

    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      Dispatchable disp = objectSystem.getPlatformClass().
          getDispatchables().get(Symbols.symbolFor("start"));
      SInvokable method = (SInvokable) disp;

      section = method.getInvokable().getSourceSection();
    } else {
      section = null;
    }

    recordActivityCreation(Events.ActorCreation, mainActor.getId(), 0,
        objectSystem.getPlatformClass().getName().getSymbolId(), section);
  }

  public final void recordActorCreation(final SFarReference actor,
      final long currentMessageId, final SourceSection sourceSection) {
    final Object value = actor.getValue();
    assert value instanceof SClass;
    final SClass actorClass = (SClass) value;

    recordActivityCreation(Events.ActorCreation, actor.getActor().getId(),
        currentMessageId, actorClass.getName().getSymbolId(), sourceSection);
  }

  private void recordActivityOrigin(final SourceSection origin) {
    assert storage.remaining() >= Events.ActivityOrigin.size :
      "Sufficient space needs to be required for encoding activity, to ensure continuous encoding in memory.";

    final int start = storage.position();

    storage.put(Events.ActivityOrigin.id);
    writeSourceSection(origin);
    assert storage.position() == start + Events.ActivityOrigin.size;
  }

  private void writeSourceSection(final SourceSection origin) {
    storage.putShort(Symbols.symbolFor(origin.getSource().getURI().toString()).getSymbolId());
    storage.putShort((short) origin.getStartLine());
    storage.putShort((short) origin.getStartColumn());
    storage.putShort((short) origin.getCharLength());
  }

  public final void recordProcessCreation(final TracingProcess proc,
      final long currentMessageId, final SourceSection sourceSection) {
    recordActivityCreation(Events.ProcessCreation, proc.getId(),
        currentMessageId,
        proc.getProcObject().getSOMClass().getName().getSymbolId(),
        sourceSection);
  }

  protected void recordActivityCreation(final Events event,
      final long activityId, final long causalMessageId, final short symbolId, final SourceSection sourceSection) {
    int requiredSpace = event.size +
        (VmSettings.TRUFFLE_DEBUGGER_ENABLED ? Events.ActivityOrigin.size : 0);
    ensureSufficientSpace(requiredSpace);

    final int start = storage.position();

    storage.put(event.id);
    storage.putLong(activityId);
    storage.putLong(causalMessageId);
    storage.putShort(symbolId);

    assert storage.position() == start + event.size;

    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      recordActivityOrigin(sourceSection);
    }
  }

  public void recordChannelCreation(final long activityId, final long channelId,
      final SourceSection section) {
    ensureSufficientSpace(Events.ChannelCreation.size);
    final int start = storage.position();

    storage.put(Events.ChannelCreation.id);
    storage.putLong(activityId);
    storage.putLong(channelId);
    writeSourceSection(section);
    assert storage.position() == start + Events.ChannelCreation.size;
    swapStorage();
  }

  public void recordProcessCompletion(final TracingProcess proc) {
    ensureSufficientSpace(Events.ProcessCompletion.size);

    final int start = storage.position();

    storage.put(Events.ProcessCompletion.id);
    storage.putLong(proc.getId());

    assert storage.position() == start + Events.ProcessCompletion.size;
  }

  public void recordTaskSpawn(final SInvokable method, final long activityId,
      final long causalMessageId, final SourceSection section) {
    recordActivityCreation(Events.TaskSpawn, activityId, causalMessageId,
        method.getSignature().getSymbolId(), section);
  }

  public void recordTaskJoin(final SInvokable method, final long activityId) {
    ensureSufficientSpace(Events.TaskJoin.size);

    final int start = storage.position();

    storage.put(Events.TaskJoin.id);
    storage.putLong(activityId);
    storage.putShort(method.getSignature().getSymbolId());

    assert storage.position() == start + Events.TaskJoin.size;
  }

  public void recordPromiseCreation(final long promiseId,
      final long causalMessageId) {
    ensureSufficientSpace(Events.PromiseCreation.size);

    final int start = storage.position();

    storage.put(Events.PromiseCreation.id);
    storage.putLong(promiseId);
    storage.putLong(causalMessageId);

    assert storage.position() == start + Events.PromiseCreation.size;
  }

  protected void recordPromiseResolution(final Events type, final long promiseId,
      final Object value, final long resolvingMessageId) {
    ensureSufficientSpace(type.size);

    final int start = storage.position();

    storage.put(type.id);
    storage.putLong(promiseId);
    storage.putLong(resolvingMessageId);
    writeParameter(value);

    assert storage.position() <= start + type.size;
  }

  public void recordPromiseResolution(final long promiseId, final Object value,
      final long resolvingMessageId) {
    recordPromiseResolution(Events.PromiseResolution, promiseId, value, resolvingMessageId);
  }

  public void recordPromiseError(final long promiseId, final Object value,
      final long resolvingMessageId) {
    recordPromiseResolution(Events.PromiseError, promiseId, value, resolvingMessageId);
  }

  public void recordPromiseChained(final long parentId, final long childId) {
    ensureSufficientSpace(Events.PromiseChained.size);

    final int start = storage.position();

    storage.put(Events.PromiseChained.id);
    storage.putLong(parentId);
    storage.putLong(childId);

    assert storage.position() == start + Events.PromiseChained.size;
  }

  public void recordChannelMessage(final long channelId, final long sender,
      final long receiver, final Object value) {
    ensureSufficientSpace(Events.ChannelMessage.size);

    final int start = storage.position();

    storage.put(Events.ChannelMessage.id);
    storage.putLong(channelId);
    storage.putLong(sender);
    storage.putLong(receiver);

    writeParameter(value);

    assert storage.position() <= start + Events.ChannelMessage.size;
  }

  private void recordMailbox(final long baseMessageId, final int mailboxNo,
      final Actor receiver) {
    final int start = storage.position();

    storage.put(Events.Mailbox.id);
    storage.putLong(baseMessageId);
    storage.putInt(mailboxNo);
    storage.putLong(receiver.getId());

    assert storage.position() == start + Events.Mailbox.size;
  }

  private void recordMailboxContinuation(final long baseMessageId,
      final int mailboxNo, final Actor receiver, final int continuationIdx) {
    final int start = storage.position();

    storage.put(Events.MailboxContd.id);
    storage.putLong(baseMessageId);
    storage.putInt(mailboxNo);
    storage.putLong(receiver.getId());
    storage.putInt(continuationIdx);

    assert storage.position() == start + Events.MailboxContd.size;
  }

  public void recordMailboxExecuted(final EventualMessage m,
      final ObjectBuffer<EventualMessage> moreCurrent, final long baseMessageId,
      final int mailboxNo, final long sendTS,
      final ObjectBuffer<Long> moreSendTS, final long[] execTS, final Actor receiver) {
    ensureSufficientSpace(Events.Mailbox.size + ActorExecutionTrace.MESSAGE_SIZE +
        m.getArgs().length * ActorExecutionTrace.PARAM_SIZE);

    recordMailbox(baseMessageId, mailboxNo, receiver);
    writeMessage(m, sendTS, VmSettings.MESSAGE_TIMESTAMPS ? execTS[0] : 0);

    int idx = 0;

    if (moreCurrent != null) {
      Iterator<Long> it = null;
      if (VmSettings.MESSAGE_TIMESTAMPS) {
        assert moreSendTS != null && moreCurrent.size() == moreSendTS.size();
        it = moreSendTS.iterator();
      }
      for (EventualMessage em : moreCurrent) {
        if (ensureSufficientSpace(ActorExecutionTrace.MESSAGE_SIZE +
            em.getArgs().length * ActorExecutionTrace.PARAM_SIZE)) {
          recordMailboxContinuation(baseMessageId, mailboxNo, receiver, idx);
        }

        writeMessage(em,
            VmSettings.MESSAGE_TIMESTAMPS ? it.next()   : 0,
            VmSettings.MESSAGE_TIMESTAMPS ? execTS[idx] : 0);
        idx++;
      }
    }
  }

  private void writeMessage(final EventualMessage em, final long sendTS,
      final long execTS) {
    if (em instanceof PromiseMessage && VmSettings.PROMISE_CREATION) {
      storage.put((byte) (ActorExecutionTrace.MESSAGE_EVENT_ID | TraceData.PROMISE_BIT));
      storage.putLong(((PromiseMessage) em).getPromise().getPromiseId());
    } else {
      storage.put(ActorExecutionTrace.MESSAGE_EVENT_ID);
    }

    storage.putLong(em.getSender().getId());
    storage.putLong(em.getCausalMessageId());
    storage.putShort(em.getSelector().getSymbolId());

    if (VmSettings.MESSAGE_TIMESTAMPS) {
      storage.putLong(execTS);
      storage.putLong(sendTS);
    }

    if (VmSettings.MESSAGE_PARAMETERS) {
      writeParameters(em.getArgs());
    }
  }

  private void writeParameters(final Object[] params) {
    storage.put((byte) (params.length - 1)); // num paramaters

    for (int i = 1; i < params.length; i++) {
      // will need a 8 plus 1 byte for most parameter,
      // boolean just use two identifiers.
      if (params[i] instanceof SFarReference) {
        Object o = ((SFarReference) params[i]).getValue();
        writeParameter(o);
      } else {
        writeParameter(params[i]);
      }
    }
  }

  private void writeParameter(final Object param) {
    // TODO: if performance critical, needs to be specialized as part of the AST
    VM.thisMethodNeedsToBeOptimized("Recoding of this data needs to be optimized for performance");
    if (param instanceof SPromise) {
      storage.put(ParamTypes.Promise.id());
      storage.putLong(((SPromise) param).getPromiseId());
    } else if (param instanceof SResolver) {
      storage.put(ParamTypes.Resolver.id());
      storage.putLong(((SResolver) param).getPromise().getPromiseId());
    } else if (param instanceof SAbstractObject) {
      storage.put(ParamTypes.Object.id());
      storage.putShort(((SAbstractObject) param).getSOMClass().getName().getSymbolId());
    } else if (param instanceof Long) {
      storage.put(ParamTypes.Long.id());
      storage.putLong((Long) param);
    } else if (param instanceof Double) {
      storage.put(ParamTypes.Double.id());
      storage.putDouble((Double) param);
    } else if (param instanceof Boolean) {
      if ((Boolean) param) {
        storage.put(ParamTypes.True.id());
      } else {
        storage.put(ParamTypes.False.id());
      }
    } else if (param instanceof String) {
      storage.put(ParamTypes.String.id());
    } else {
      throw new RuntimeException("unexpected parameter type");
    }
    // TODO add case for null/nil/exception,
    // ask ctorresl about what type is used for the error handling stuff
  }

  public void recordMailboxExecutedReplay(final Queue<EventualMessage> todo,
      final long baseMessageId, final int mailboxNo, final Actor receiver) {
    ensureSufficientSpace(Events.Mailbox.size + 100 * 50);
    recordMailbox(baseMessageId, mailboxNo, receiver);

    int idx = 0;

    if (todo != null) {
      for (EventualMessage em : todo) {
        if (ensureSufficientSpace(ActorExecutionTrace.MESSAGE_SIZE +
            em.getArgs().length * ActorExecutionTrace.PARAM_SIZE)) {
          recordMailboxContinuation(baseMessageId, mailboxNo, receiver, idx);
        }

        writeMessage(em, 0, 0);

        if (VmSettings.MESSAGE_PARAMETERS) {
          writeParameters(em.getArgs());
        }
        idx++;
      }
    }
  }

  private static class SyncedTraceBuffer extends TraceBuffer {
    protected SyncedTraceBuffer() { super(); }

    @Override
    protected synchronized void recordActivityCreation(final Events event,
        final long activityId, final long causalMessageId, final short symbolId,
        final SourceSection section) {
      super.recordActivityCreation(event, activityId, causalMessageId, symbolId, section);
    }

    @Override
    public synchronized void recordProcessCompletion(final TracingProcess proc) {
      super.recordProcessCompletion(proc);
    }

    @Override
    public synchronized void recordPromiseCreation(final long promiseId, final long causalMessageId) {
      super.recordPromiseCreation(promiseId, causalMessageId);
    }

    @Override
    protected synchronized void recordPromiseResolution(final Events type, final long promiseId,
        final Object value, final long resolvingMessageId) {
      super.recordPromiseResolution(type, promiseId, value, resolvingMessageId);
    }

    @Override
    public synchronized void recordPromiseChained(final long parentId, final long childId) {
      super.recordPromiseChained(parentId, childId);
    }

    @Override
    public synchronized void recordMailboxExecuted(final EventualMessage m,
        final ObjectBuffer<EventualMessage> moreCurrent, final long baseMessageId,
        final int mailboxNo, final long sendTS,
        final ObjectBuffer<Long> moreSendTS, final long[] execTS,
        final Actor receiver) {
      super.recordMailboxExecuted(m, moreCurrent, baseMessageId, mailboxNo,
          sendTS, moreSendTS, execTS, receiver);
    }

    @Override
    public synchronized void recordMailboxExecutedReplay(final Queue<EventualMessage> todo,
        final long baseMessageId, final int mailboxNo, final Actor receiver) {
      super.recordMailboxExecutedReplay(todo, baseMessageId, mailboxNo, receiver);
    }

    @Override
    public synchronized void recordChannelCreation(final long activityId,
        final long channelId, final SourceSection section) {
      super.recordChannelCreation(activityId, channelId, section);
    }
  }
}
