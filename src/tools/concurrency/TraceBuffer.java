package tools.concurrency;

import som.vm.VmSettings;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;


public abstract class TraceBuffer {

  public static TraceBuffer create(final long threadId) {
    assert VmSettings.ACTOR_TRACING || VmSettings.KOMPOS_TRACING;
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      return new KomposTrace.KomposTraceBuffer(threadId);
    } else {
      return new ActorTraceBuffer();
    }
  }

  protected ByteBuffer storage;

  protected TraceBuffer() {
    retrieveBuffer();
  }

  public void retrieveBuffer() {
    this.storage = TracingBackend.getEmptyBuffer();
  }

  public void returnBuffer() {
    TracingBackend.returnBuffer(storage);
    storage = null;
  }

  public boolean isEmpty() {
    return storage.position() == 0;
  }

  public boolean isFull() {
    return storage.remaining() == 0;
  }

  boolean swapStorage() {
    TracingBackend.returnBuffer(storage);
    retrieveBuffer();
    return true;
  }

  protected final void ensureSufficientSpace(final int requiredSpace) {
    if (storage.remaining() < requiredSpace) {
      swapBufferWhenNotEnoughSpace();
    }
  }

  protected void swapBufferWhenNotEnoughSpace() {
    swapStorage();
  }
}
