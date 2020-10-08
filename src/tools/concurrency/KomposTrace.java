package tools.concurrency;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.SourceSection;

import bd.source.SourceCoordinate;
import som.interpreter.Types;
import som.interpreter.actors.Actor;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.Activity;
import som.vm.ObjectSystem;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.debugger.PrimitiveCallOrigin;
import tools.debugger.entities.*;

import java.util.*;


public class KomposTrace {

  private static Map<Long, List<Integer>> buffersByActor = new HashMap<>();
  private static Map<Long, List<Long>> messagesReceivedByActor = new HashMap<>();

  public static boolean missingBuffers(long actorSuspendedId) {
    List<Integer> buffers = buffersByActor.get(actorSuspendedId);
    Collections.sort(buffers);

    for (int i = 1; i < buffers.size(); i++) {
      int previous = buffers.get(i - 1);
      int current = buffers.get(i);
      if (current != (previous + 1)) {
        return true;
      }
    }

    return false;
  }

  public static void recordMainActor(final Actor mainActor,
      final ObjectSystem objectSystem) {
    KomposTraceBuffer buffer = KomposTraceBuffer.create(0);
    buffer.recordCurrentActivity(mainActor);
    buffer.recordMainActor(mainActor, objectSystem);
    buffer.recordSendOperation(SendOp.ACTOR_MSG, 0, mainActor.getId(), mainActor, (short) 0, 0, null, null);
    buffer.returnBuffer(null);
  }

  public static void currentActivity(final Activity current) {
    TracingActivityThread t = getThread();
    ((KomposTraceBuffer) t.getBuffer()).recordCurrentActivity(current);
  }

  public static void clearCurrentActivity(final Activity current) {
    TracingActivityThread t = getThread();
    ((KomposTraceBuffer) t.getBuffer()).resetLastActivity();
  }

  public static void activityCreation(final ActivityType entity, final long activityId,
      final SSymbol name, final SourceSection section) {
    TracingActivityThread t = getThread();
    ((KomposTraceBuffer) t.getBuffer()).recordActivityCreation(entity, activityId,
        name.getSymbolId(), section, t.getActivity());
  }

  public static SourceSection getPrimitiveCaller(final SourceSection section) {
    SourceSection s;
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED && section.getSource().isInternal()) {
      s = PrimitiveCallOrigin.getCaller();
    } else {
      s = section;
    }
    return s;
  }

  public static void activityCompletion(final ActivityType event) {
    TracingActivityThread t = getThread();
    ((KomposTraceBuffer) t.getBuffer()).recordActivityCompletion(event, t.getActivity());
  }

  public static void scopeStart(final DynamicScopeType entity, final long scopeId,
      final SourceSection section) {
    TracingActivityThread t = getThread();
    ((KomposTraceBuffer) t.getBuffer()).recordScopeStart(entity, scopeId, section,
        t.getActivity());
  }

  public static void scopeEnd(final DynamicScopeType entity) {
    TracingActivityThread t = getThread();
    ((KomposTraceBuffer) t.getBuffer()).recordScopeEnd(entity, t.getActivity());
  }

  public static void promiseResolution(final long promiseId, final Object value) {
    Thread current = Thread.currentThread();

    assert current instanceof TracingActivityThread;
    TracingActivityThread t = (TracingActivityThread) current;

    String name = Types.toDebuggerString(value);
    byte[] nameBytes = name.getBytes();

    ((KomposTraceBuffer) t.getBuffer()).recordSendOperation(SendOp.PROMISE_RESOLUTION, 0,
        promiseId,
        t.getActivity(), (short) 0, 0, null, nameBytes);
    t.resolvedPromises++;
  }

  public static void promiseError(final long promiseId, final Object value) {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    TracingActivityThread t = (TracingActivityThread) current;
    String error = Types.toDebuggerString(value);
    byte[] errorBytes = error.getBytes();
    ((KomposTraceBuffer) t.getBuffer()).recordSendOperation(SendOp.PROMISE_RESOLUTION, 0,
        promiseId,
        t.getActivity(), (short) 0, 0, null, errorBytes);
    t.erroredPromises++;
  }

  /**
   * Record chaining of promises.
   *
   * @param promiseValueId, the promise that is used to resolve another promise
   * @param promiseId, the promise that is being resolved
   */
  public static void promiseChained(final long promiseValueId, final long promiseId) {
    TracingActivityThread t = getThread();
    ((KomposTraceBuffer) t.getBuffer()).recordSendOperation(
        SendOp.PROMISE_RESOLUTION, promiseValueId, promiseId, t.getActivity(), (short) 0, 0, null, null);
    t.resolvedPromises++;
  }

  public static void sendOperation(final SendOp op, final long entityId,
      final long targetId, final SSymbol selector, long targetActorId, SourceSection msgSourceCoordinate) {
    TracingActivityThread t = getThread();
    ((KomposTraceBuffer) t.getBuffer()).recordSendOperation(op, entityId, targetId,
        t.getActivity(), selector.getSymbolId(), targetActorId, msgSourceCoordinate, null);
  }

  public static void receiveOperation(final ReceiveOp op, final long sourceId) {
    TracingActivityThread t = getThread();
    ((KomposTraceBuffer) t.getBuffer()).recordReceiveOperation(op, sourceId, t.getActivity());
  }

  public static void passiveEntityCreation(final PassiveEntityType entity,
      final long entityId, final SourceSection section) {
    TracingActivityThread t = getThread();
    ((KomposTraceBuffer) t.getBuffer()).recordPassiveEntityCreation(entity, entityId, section,
        t.getActivity());
  }

  private static TracingActivityThread getThread() {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    return (TracingActivityThread) current;
  }

  public static void recordSuspendedActivityByDebugger(TracingActivityThread t) {
    ((KomposTraceBuffer) t.getBuffer()).recordPausedActivity(t.getActivity());
  }

  public static void actorMessageReception(long messageId, TracingActivityThread t) {
//    System.out.println("***** "+((Actor.ActorProcessingThread)t).getCurrentActor() +" activity "+((Actor.ActorProcessingThread)t).getActivity());
    Activity activity = t.getActivity();
    if (activity == null) {
      activity = ((Actor.ActorProcessingThread) t).getCurrentActor();
    }

    if (!messageReceivedRecorded(messageId, activity.getId())) {
      ((KomposTraceBuffer) t.getBuffer()).recordMessageReceived(MessageReception.MESSAGE_RCV, activity, messageId);
    }
  }

  /**
   * Check the message has not been saved before.
   * Messages received can be repeated because we record them at the point where the message is sent and when the messages are processed.
   * This is needed because at the point where the message is append in the mailbox the TracingActivityThread may not be available,
   * they become available when the actor is about to process the messages.
   *
   * @param messageId
   * @param actorId
   * @return
   */
  private static boolean messageReceivedRecorded(long messageId, long actorId) {
    List<Long> messages;
    if (!messagesReceivedByActor.containsKey(actorId)) {
      messages = new ArrayList<>();
    } else {
      messages = messagesReceivedByActor.get(actorId);
    }

    if (messages.contains(messageId)) {
      return true;
    } else { //new message
      messages.add(messageId);
      messagesReceivedByActor.put(actorId, messages);
    }
    return false;
  }

  public static class KomposTraceBuffer extends TraceBuffer {

    /**
     * Id of the implementation-level thread.
     * Thus, not an application-level thread.
     */
    protected final long implThreadId;

    /** Id of the last activity that was running on this buffer. */
    private Activity lastActivity;

    public static KomposTraceBuffer create(final long implThreadId) {
      assert VmSettings.KOMPOS_TRACING;
      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        return new SyncedKomposTraceBuffer(implThreadId);
      } else {
        return new KomposTraceBuffer(implThreadId);
      }
    }

    protected KomposTraceBuffer(final long implThreadId) {
      this.implThreadId = implThreadId;
      this.lastActivity = null;

      recordThreadId();
    }

    boolean swapStorage(final Activity current) {
      if (buffer == null ||
          position <= (Implementation.IMPL_THREAD.getSize() +
              Implementation.IMPL_CURRENT_ACTIVITY.getSize())) {
        return false;
      }
      super.swapStorage();
      this.lastActivity = null;
      recordCurrentActivity(current);
      return true;
    }

    public void recordPausedActivity(final Activity current) {
      recordCurrentActivity(current);
    }

    @TruffleBoundary
    protected boolean ensureSufficientSpace(final int requiredSpace, final Activity current) {
      if ((position + requiredSpace) >= VmSettings.BUFFER_SIZE) {
        boolean didSwap = swapStorage(current);
        return didSwap;
      }
      return false;
    }

    public void resetLastActivity() {
      lastActivity = null;
    }

    public final void recordMainActor(final Actor mainActor,
        final ObjectSystem objectSystem) {
      SourceSection section;

      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        Dispatchable disp =
            objectSystem.getPlatformClass().getDispatchables().get(Symbols.symbolFor("start"));
        SInvokable method = (SInvokable) disp;

        section = method.getInvokable().getSourceSection();
      } else {
        section = null;
      }

      recordActivityCreation(ActivityType.ACTOR, mainActor.getId(),
          objectSystem.getPlatformClass().getName().getSymbolId(), section, mainActor);
    }

    private void recordThreadId() {
      final int start = position;
      assert start == 0;

      put(Implementation.IMPL_THREAD.getId());
      putLong(implThreadId);

//      System.out.println("thread "+implThreadId);

      assert position == start + Implementation.IMPL_THREAD.getSize();
    }

    public void recordCurrentActivity(final Activity current) {

      if (current == null) {
        return;
      }

      ensureSufficientSpace(Implementation.IMPL_CURRENT_ACTIVITY.getSize(), current);

      lastActivity = current;

      final int start = position;

      put(Implementation.IMPL_CURRENT_ACTIVITY.getId());
      long actorId = current.getId();
      int bufferId = current.getNextTraceBufferId();

      putLong(actorId);
      putInt(bufferId);

//      System.out.println("recordCurrentActivity "+actorId +" "+bufferId);

      List<Integer> bufferList;
      if (buffersByActor.containsKey(actorId)) {
        bufferList = buffersByActor.get(actorId);
      } else {
        bufferList = new ArrayList<>();
      }
      bufferList.add(bufferId);
      buffersByActor.put(actorId, bufferList);

      assert position == start + Implementation.IMPL_CURRENT_ACTIVITY.getSize();
    }

    /** REM: Ensure it is in sync with {@link TraceSemantics#SOURCE_SECTION_SIZE}. */
    private void writeSourceSection(final SourceSection origin) {
      /*
       * TODO: make sure there is always a sourcesection
       * right now promises created by getChainedPromiseFor have no sourceSection and
       * caused a Nullpointer exception in this method.
       * The following if is a workaround.
       */
      if (origin == null) {
        putLong(0);
        return;
      }

      assert !origin.getSource()
                    .isInternal()
          : "Need special handling to ensure we see user code reported to trace/debugger";
      putShort(Symbols.symbolFor(SourceCoordinate.getURI(origin.getSource())).getSymbolId());
      putShort((short) origin.getStartLine());
      putShort((short) origin.getStartColumn());
      putShort((short) origin.getCharLength());
    }

    public void recordActivityCreation(final ActivityType entity, final long activityId,
        final short symbolId, final SourceSection sourceSection, final Activity current) {
      int requiredSpace = entity.getCreationSize();
      ensureSufficientSpace(requiredSpace, current);

      final int start = position;

      assert entity.getCreationMarker() != 0;

      put(entity.getCreationMarker());
      putLong(activityId);
      putShort(symbolId);

      if (VmSettings.KOMPOS_TRACING) {
        writeSourceSection(sourceSection);
      }
      assert position == start + requiredSpace;
    }

    public void recordActivityCompletion(final ActivityType entity, final Activity current) {
      int requireSize = entity.getCompletionSize();
      ensureSufficientSpace(requireSize, current);

      final int start = position;
      put(entity.getCompletionMarker());
      assert position == start + requireSize;
    }

    private void recordEventWithIdAndSource(final byte eventMarker, final int eventSize,
        final long id, final SourceSection section, final Activity current) {
      ensureSufficientSpace(eventSize, current);

      final int start = position;

      put(eventMarker);
      putLong(id);

      if (VmSettings.KOMPOS_TRACING) {
        writeSourceSection(section);
      }
      assert position == start + eventSize;
    }

    public void recordScopeStart(final DynamicScopeType entity, final long scopeId,
        final SourceSection section, final Activity current) {
      recordEventWithIdAndSource(entity.getStartMarker(), entity.getStartSize(),
          scopeId, section, current);
    }

    public void recordScopeEnd(final DynamicScopeType entity, final Activity current) {
      int requiredSpace = entity.getEndSize();
      ensureSufficientSpace(requiredSpace, current);

      final int start = position;
      put(entity.getEndMarker());

      assert position() == start + requiredSpace;
    }

    public void recordPassiveEntityCreation(final PassiveEntityType entity,
        final long entityId, final SourceSection section, final Activity current) {
      recordEventWithIdAndSource(entity.getCreationMarker(),
          entity.getCreationSize(), entityId, section, current);
    }

    public void recordReceiveOperation(final ReceiveOp op, final long sourceId,
        final Activity current) {
      int requiredSpace = op.getSize();
      ensureSufficientSpace(requiredSpace, current);

      final int start = position;
      put(op.getId());
      putLong(sourceId);

      assert position == start + requiredSpace;
    }

    public void recordSendOperation(final SendOp op, final long entityId,
        final long targetId, final Activity current, final short symbolId, long targetActorId, SourceSection msgSourceCoordinate, byte[] value) {
      int requiredSpace;
      if (value == null) {
        requiredSpace = op.getSize();
      } else {
        requiredSpace = op.getSize(value);
      }

      ensureSufficientSpace(requiredSpace, current);

      final int start = position;
      put(op.getId());
      putLong(entityId);
      putLong(targetId);
      putLong(targetActorId);
      putShort(symbolId);

      if (VmSettings.KOMPOS_TRACING) {
         writeSourceSection(msgSourceCoordinate);
      }

      if(value != null) {
        putInt(value.length);
        for (byte b : value) {
          put(b);
        }
      }

      assert position == start + requiredSpace;
    }

    public void recordMessageReceived(MessageReception mr, final Activity current, final long messageId) {
      int requiredSpace = mr.getSize();
      ensureSufficientSpace(requiredSpace, current);

      final int start = position;
      put(mr.getId());
      putLong(messageId);

      System.out.println("-message received "+messageId +" actor "+current.getId());

      if (position != start + requiredSpace ) {
        System.out.println();
      }

      assert position == start + requiredSpace;
    }

    public static class SyncedKomposTraceBuffer extends KomposTraceBuffer {

      protected SyncedKomposTraceBuffer(final long implThreadId) {
        super(implThreadId);
      }

      @Override
      public synchronized void recordActivityCreation(final ActivityType entity,
          final long activityId, final short symbolId,
          final SourceSection section, final Activity current) {
        super.recordActivityCreation(entity, activityId, symbolId, section, current);
      }

      @Override
      public synchronized void recordScopeStart(final DynamicScopeType entity,
          final long scopeId, final SourceSection section, final Activity current) {
        super.recordScopeStart(entity, scopeId, section, current);
      }

      @Override
      public synchronized void recordScopeEnd(final DynamicScopeType entity,
          final Activity current) {
        super.recordScopeEnd(entity, current);
      }

      @Override
      public synchronized void recordPassiveEntityCreation(final PassiveEntityType entity,
          final long entityId, final SourceSection section, final Activity current) {
        super.recordPassiveEntityCreation(entity, entityId, section, current);
      }

      @Override
      public synchronized void recordActivityCompletion(final ActivityType entity,
          final Activity current) {
        super.recordActivityCompletion(entity, current);
      }

      @Override
      public synchronized void recordReceiveOperation(final ReceiveOp op,
          final long sourceId, final Activity current) {
        super.recordReceiveOperation(op, sourceId, current);
      }

      @Override
      public synchronized void recordSendOperation(final SendOp op,
          final long entityId, final long targetId, final Activity current, final short symbol, final long targetActorId, final SourceSection section, byte[] value) {
        super.recordSendOperation(op, entityId, targetId, current, symbol, targetActorId, section, value);
      }
    }
  }

}
