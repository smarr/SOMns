package tools.concurrency;

import som.interpreter.processes.SChannel;

public final class TracingChannel extends SChannel {
  protected final long channelId;

  public TracingChannel() {
    Thread current = Thread.currentThread();
    assert current instanceof TracingActivityThread;
    channelId = ((TracingActivityThread) current).generateActivityId();
  }

  public long getId() {
    return channelId;
  }
}
