package tools.concurrency;

public interface SExternalDataSource {

  /**
   * This is the method that is called in replay when an actor expects an external message.
   * The actor looks up the datasource through the id of its actor, and then calls this method
   * on it with the needed data. This method will then cause the message to be created and
   * sent.
   */
  void requestExternalMessage(short method, int dataId);
}
