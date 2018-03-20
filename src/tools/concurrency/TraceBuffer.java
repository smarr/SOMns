package tools.concurrency;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.vm.VmSettings;
import tools.concurrency.ActorExecutionTrace.ActorTraceBuffer;


public class TraceBuffer {

  public static TraceBuffer create() {
    assert VmSettings.ACTOR_TRACING || VmSettings.MEDEOR_TRACING;
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      return MedeorTrace.MedeorTraceBuffer.create();
    } else {
      return new ActorTraceBuffer();
    }
  }

  /**
   * Id of the implementation-level thread.
   * Thus, not an application-level thread.
   */
  protected long implThreadId;

  protected ByteBuffer storage;

  public void init(final ByteBuffer storage, final long implThreadId) {
    this.storage = storage;
    this.implThreadId = implThreadId;
    assert storage.order() == ByteOrder.BIG_ENDIAN;
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
    init(TracingBackend.getEmptyBuffer(), implThreadId);
    return true;
  }

  @TruffleBoundary
  protected boolean ensureSufficientSpace(final int requiredSpace) {
    if (storage.remaining() < requiredSpace) {
      boolean didSwap = swapStorage();
      assert didSwap;
      return didSwap;
    }
    return false;
  }

}
