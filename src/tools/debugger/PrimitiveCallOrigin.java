package tools.debugger;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.MessageSendNode.GenericMessageSendNode;


public class PrimitiveCallOrigin {
  public static Node getCallerNode() {
    int[] level = new int[1];

    FrameInstance f = Truffle.getRuntime().iterateFrames(fi -> {
      if (level[0] == 2) {
        return fi;
      }
      level[0]++;
      return null;
    });
    return f.getCallNode();
  }

  public static SourceSection getCaller() {
    Node directCallNode = getCallerNode();
    Node current = directCallNode;

    while (!(current instanceof GenericMessageSendNode)) {
      current = current.getParent();
    }
    return current.getSourceSection();
  }
}
