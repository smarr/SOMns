package tools.debugger.message;

import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.RootNode;
import org.java_websocket.WebSocket;
import som.interpreter.SomLanguage;
import tools.debugger.FrontendConnector;
import tools.debugger.frontend.ApplicationThreadTask;
import tools.debugger.frontend.Suspension;
import tools.debugger.message.Message.IncommingMessage;
import tools.debugger.frontend.ApplicationThreadTask;

import java.util.ArrayList;


public class UpdateClass extends IncommingMessage {
  private final String classToRecompile ;

  public UpdateClass(String classToRecompile) {
    this.classToRecompile = classToRecompile;
  }

  @Override public void process(FrontendConnector connector, WebSocket conn) {
    Suspension suspension = connector.getSuspension(0);
    UpdateClassTask updateTask = new UpdateClassTask(connector,suspension,classToRecompile);
    suspension.submitTask(updateTask);

    //suspension.resume();
    //connector.updateClass(classToRecompile);
    System.out.println("Done Recompilation");
    System.out.println(classToRecompile);
  }

  private class UpdateClassTask extends ApplicationThreadTask {

    private final FrontendConnector frontend;
    private final Suspension        suspension;
    private final String filePath;

    UpdateClassTask(final FrontendConnector frontend, final Suspension suspension, String filePath) {
      this.frontend = frontend;
      this.suspension = suspension;
      this.filePath = filePath;

    }

    @Override
    public boolean execute() {
      frontend.updateClass(filePath);
      ArrayList<DebugStackFrame> frames =  suspension.getStackFrames();
      int a = 1 + 1;
      System.out.println(a);
      //suspension.getEvent().prepareUnwindFrame(suspension.getFrame(2));
      //suspension.resume();
     // System.out.println(suspension.getEvent().getTopStackFrame().getName());

//      RootNode node = suspension.getStackFrames().get(2).getRawNode(SomLanguage.class).getRootNode();
//
//        System.out.println(node.getName());
//        System.out.println(node.getCallTarget().);
//      DebugStackFrame frame =  suspension.getStackFrames().get(2);
//      System.out.println(frame.getName());
//      Frame realFrame = frame.getRawFrame(SomLanguage.class, FrameInstance.FrameAccess.READ_ONLY);
//      System.out.println("GOT REAL FRAME");
//
      return true;
    }
  }
}
