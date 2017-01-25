package tools.concurrency;


public abstract class Tags {


  /** Marks the eventual message send operator. */
  public final class EventualMessageSend extends Tags {
    private EventualMessageSend() { }
  }

  /** Marks a read operation on a channel. */
  public final class ChannelRead extends Tags {
    private ChannelRead() { }
  }

  /** Marks a write operation on a channel. */
  public final class ChannelWrite extends Tags {
    private ChannelWrite() { }
  }

  /** Marks an expression that can be target of a breakpoint. */
  public final class ExpressionBreakpoint extends Tags {
    private ExpressionBreakpoint() { }
  }
}
