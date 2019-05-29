package tools.parser;

public class PromiseMsgObj extends MsgObj {
  long promiseId;

  public PromiseMsgObj(final long messageId, final long senderId, final long parentMsgId,
      final long promiseId) {
    super(messageId, senderId, parentMsgId);
    this.promiseId = promiseId;
  }
}
