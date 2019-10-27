package tools.replay;

public class ReplayRecord {
  public static class MessageRecord {
    // not subclassing ReplayRecord because we want to keep this separate
    public final long sender;

    public MessageRecord(final long sender) {
      super();
      this.sender = sender;
    }

    public boolean isExternal() {
      return false;
    }
  }

  public static class ExternalMessageRecord extends MessageRecord {
    public final short method;
    public final int   dataId;

    public ExternalMessageRecord(final long sender, final short method, final int dataId) {
      super(sender);
      this.method = method;
      this.dataId = dataId;
    }

    @Override
    public boolean isExternal() {
      return true;
    }
  }

  public static class PromiseMessageRecord extends MessageRecord {
    public long pId;

    public PromiseMessageRecord(final long sender, final long resolver) {
      super(sender);
      this.pId = resolver;
    }
  }

  public static class NumberedPassiveRecord extends ReplayRecord {
    public final long passiveEntityId;
    public final long eventNo;

    public NumberedPassiveRecord(final long passiveEntityId, final long eventNo) {
      this.passiveEntityId = passiveEntityId;
      this.eventNo = eventNo;
    }
  }

  public static class ExternalPromiseMessageRecord extends PromiseMessageRecord {
    public final int   dataId;
    public final short method;

    public ExternalPromiseMessageRecord(final long sender, final long resolver,
        final short method,
        final int extData) {
      super(sender, resolver);
      this.method = method;
      this.dataId = extData;
    }

    @Override
    public boolean isExternal() {
      return true;
    }
  }

  public static class IsLockedRecord extends ReplayRecord {
    public final long    lockId;
    public final boolean isLocked;

    public IsLockedRecord(final long lockid, final long isLocked) {
      this.lockId = lockid;
      this.isLocked = isLocked == 1;
    }
  }

  public static class AwaitTimeoutRecord extends ReplayRecord {
    public final boolean isSignaled;

    public AwaitTimeoutRecord(final long isSignaled) {
      this.isSignaled = isSignaled == 1;
    }
  }
}
