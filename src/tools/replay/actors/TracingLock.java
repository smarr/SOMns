package tools.replay.actors;

import java.util.concurrent.locks.ReentrantLock;

import tools.concurrency.TracingActivityThread;
import tools.replay.PassiveEntityWithEvents;


public class TracingLock extends ReentrantLock implements PassiveEntityWithEvents {
  private static final long serialVersionUID = -3346973644925799901L;
  long                      id;
  int                       eventNo          = 0;

  public TracingLock() {
    id = TracingActivityThread.newEntityId();
  }

  public long getId() {
    return id;
  }

  @Override
  public int getNextEventNumber() {
    return eventNo;
  }
}
