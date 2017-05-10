package tools.concurrency;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.Actor;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.Activity;
import som.vm.ObjectSystem;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import tools.SourceCoordinate;
import tools.concurrency.ActorExecutionTrace.Events;

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

  /** Id of the implementation-level thread.
      Thus, not an application-level thread. */
  private long implThreadId;

  /** Id of the last activity that was running on this buffer. */
  private long lastActivityId;

  protected TraceBuffer() { }

  public void init(final ByteBuffer storage, final long implThreadId) {
    this.storage = storage;
    this.implThreadId = implThreadId;
    assert storage.order() == ByteOrder.BIG_ENDIAN;
    recordThreadId();
  }

  public void returnBuffer() {
    ActorExecutionTrace.returnBuffer(storage);
    storage = null;
  }

  public boolean isEmpty() {
    return storage.position() == 0;
  }

  public boolean isFull() {
    return storage.remaining() == 0;
  }

  boolean swapStorage(final Activity current) {
    if (storage == null || storage.position() <= Events.ImplThread.size) {
      return false;
    }
    ActorExecutionTrace.returnBuffer(storage);
    init(ActorExecutionTrace.getEmptyBuffer(), implThreadId);
    recordCurrentActivity(current);
    return true;
  }

  private void recordThreadId() {
    final int start = storage.position();
    assert start == 0;

    storage.put(Events.ImplThread.id);
    storage.putLong(implThreadId);

    assert storage.position() == start + Events.ImplThread.size;
  }

  public void recordCurrentActivity(final Activity current) {
    final int start = storage.position();

    storage.put(Events.ImplThreadCurrentActivity.id);
    storage.putLong(current.getId());
    storage.putInt(current.getNextTraceBufferId());

    assert storage.position() == start + Events.ImplThreadCurrentActivity.size;
  }

  protected boolean ensureSufficientSpace(final int requiredSpace,
      final Activity current) {
    if (storage.remaining() < requiredSpace) {
      boolean didSwap = swapStorage(current);
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

    recordActivityCreation(Events.ActorCreation, mainActor.getId(),
        objectSystem.getPlatformClass().getName().getSymbolId(), section, mainActor);
  }

  private static final int SOURCE_SECTION_LENGTH = 8;

  private void writeSourceSection(final SourceSection origin) {
    assert !origin.getSource().isInternal() :
      "Need special handling to ensure we see user code reported to trace/debugger";
    storage.putShort(SourceCoordinate.getURI(origin.getSource()).getSymbolId());
    storage.putShort((short) origin.getStartLine());
    storage.putShort((short) origin.getStartColumn());
    storage.putShort((short) origin.getCharLength());
  }

  public void recordActivityCreation(final Events event, final long activityId,
      final short symbolId, final SourceSection sourceSection, final Activity current) {
    int requiredSpace = event.size +
        (VmSettings.TRUFFLE_DEBUGGER_ENABLED ? SOURCE_SECTION_LENGTH : 0);
    ensureSufficientSpace(requiredSpace, current);

    final int start = storage.position();

    storage.put(event.id);
    storage.putLong(activityId);
    storage.putShort(symbolId);

    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      writeSourceSection(sourceSection);
    }
    assert storage.position() == start + requiredSpace;
  }

  public void recordActivityCompletion(final Events event, final Activity current) {
    ensureSufficientSpace(event.size, current);

    final int start = storage.position();
    storage.put(event.id);
    assert storage.position() == start + event.size;
  }

  private void recordEventWithIdAndSource(final Events event, final long id,
      final SourceSection section, final Activity current) {
    int requiredSpace = event.size +
        (VmSettings.TRUFFLE_DEBUGGER_ENABLED ? SOURCE_SECTION_LENGTH : 0);
    ensureSufficientSpace(requiredSpace, current);

    final int start = storage.position();

    storage.put(event.id);
    storage.putLong(id);

    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      writeSourceSection(section);
    }
    assert storage.position() == start + requiredSpace;
  }

  public void recordScopeStart(final Events event, final long scopeId,
      final SourceSection section, final Activity current) {
    recordEventWithIdAndSource(event, scopeId, section, current);
  }

  public void recordScopeEnd(final Events event, final Activity current) {
    ensureSufficientSpace(event.size, current);

    final int start = storage.position();
    storage.put(event.id);

    assert storage.position() == start + event.size;
  }

  public void recordEntityCreation(final Events event, final long entityId,
      final SourceSection section, final Activity current) {
    recordEventWithIdAndSource(event, entityId, section, current);
  }

  public void recordReceiveOperation(final Events event, final long sourceId,
      final Activity current) {
    ensureSufficientSpace(event.size, current);

    final int start = storage.position();
    storage.put(event.id);
    storage.putLong(sourceId);

    assert storage.position() == start + event.size;
  }

  public void recordSendOperation(final Events event, final long entityId, final long targetId, final Activity current) {
    ensureSufficientSpace(event.size, current);

    final int start = storage.position();
    storage.put(event.id);
    storage.putLong(entityId);
    storage.putLong(targetId);

    assert storage.position() == start + event.size;
  }

  private static class SyncedTraceBuffer extends TraceBuffer {
    protected SyncedTraceBuffer() { super(); }

    @Override
    public synchronized void recordActivityCreation(final Events event,
        final long activityId, final short symbolId,
        final SourceSection section, final Activity current) {
      super.recordActivityCreation(event, activityId, symbolId, section, current);
    }

    @Override
    public synchronized void recordScopeStart(final Events event,
        final long scopeId, final SourceSection section, final Activity current) {
      super.recordScopeStart(event, scopeId, section, current);
    }

    @Override
    public synchronized void recordScopeEnd(final Events event,
        final Activity current) {
      super.recordScopeEnd(event, current);
    }

    @Override
    public synchronized void recordEntityCreation(final Events event,
        final long entityId, final SourceSection section,
        final Activity current) {
      super.recordEntityCreation(event, entityId, section, current);
    }

    @Override
    public synchronized void recordActivityCompletion(final Events event,
        final Activity current) {
      super.recordActivityCompletion(event, current);
    }

    @Override
    public synchronized void recordReceiveOperation(final Events event,
        final long sourceId, final Activity current) {
      super.recordReceiveOperation(event, sourceId, current);
    }

    @Override
    public synchronized void recordSendOperation(final Events event,
        final long entityId, final long targetId, final Activity current) {
      super.recordSendOperation(event, entityId, targetId, current);
    }
  }
}
