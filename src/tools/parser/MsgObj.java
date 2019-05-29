package tools.parser;

public class MsgObj {
  long messageId;
  long senderId;
  long receiverId;
  long parentMsgId;

  public MsgObj(final long messageId, final long senderId, final long parentMsgId) {
    this.messageId = messageId;
    this.senderId = senderId;
    this.parentMsgId = parentMsgId;
  }

  public MsgObj(final long messageId, final long senderId, final long receiverId,
      final long parentMsgId) {
    this.messageId = messageId;
    this.senderId = senderId;
    this.receiverId = receiverId;
    this.parentMsgId = parentMsgId;
  }
}
