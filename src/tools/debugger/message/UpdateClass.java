package tools.debugger.message;

import org.java_websocket.WebSocket;
import tools.debugger.FrontendConnector;
import tools.debugger.message.Message.IncommingMessage;


public class UpdateClass extends IncommingMessage {
  private final String classToRecompile ;

  public UpdateClass(String classToRecompile) {
    this.classToRecompile = classToRecompile;
  }

  @Override public void process(FrontendConnector connector, WebSocket conn) {
    connector.updateClass(classToRecompile);
    System.out.println("Done Recompilation");
    System.out.println(classToRecompile);
  }
}
