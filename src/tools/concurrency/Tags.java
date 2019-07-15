package tools.concurrency;

import com.oracle.truffle.api.instrumentation.Tag;


public abstract class Tags {

  /** Marks the eventual message send operator. */
  public final class EventualMessageSend extends Tag {
    private EventualMessageSend() {}
  }

  /** Marks a read operation on a channel. */
  public final class ChannelRead extends Tag {
    private ChannelRead() {}
  }

  /** Marks a write operation on a channel. */
  public final class ChannelWrite extends Tag {
    private ChannelWrite() {}
  }

  /** Marks an expression that can be target of a breakpoint. */
  public final class ExpressionBreakpoint extends Tag {
    private ExpressionBreakpoint() {}
  }

  /** Marks the createPromisePair primitive. */
  public final class CreatePromisePair extends Tag {
    private CreatePromisePair() {}
  }

  /** Marks the whenResolved primitive. */
  public final class WhenResolved extends Tag {
    private WhenResolved() {}
  }

  /** Marks the whenResolvedOnError primitive. */
  public final class WhenResolvedOnError extends Tag {
    private WhenResolvedOnError() {}
  }

  /** Marks the onError primitive. */
  public final class OnError extends Tag {
    private OnError() {}
  }

  /** Marks the creation of an activity. */
  public final class ActivityCreation extends Tag {
    private ActivityCreation() {}
  }

  /** Marks the join operation of an activity. */
  public final class ActivityJoin extends Tag {
    private ActivityJoin() {}
  }

  /** Marks the transactional operation. */
  public final class Atomic extends Tag {
    private Atomic() {}
  }

  /** Marks the locking operation. */
  public final class AcquireLock extends Tag {
    private AcquireLock() {}
  }

  /** Marks the release operation. */
  public final class ReleaseLock extends Tag {
    private ReleaseLock() {}
  }

  /**
   * Marks the source section of a method's prototype, i.e., declaration.
   * NOTE: Special Tag, applied automatically be front-end.
   */
  public final class MethodDeclaration extends Tag {
    private MethodDeclaration() {}
  }
}
