package som.interop;

import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.Node;

import som.primitives.threading.TaskThreads.SomTaskOrThread;


@MessageResolution(receiverType = SomTaskOrThread.class)
public class SomTaskOrThreadInteropMessages {
  @Resolve(message = "IS_NULL")
  abstract static class NullCheckNode extends Node {
    public Object access(final SomTaskOrThread object) {
      return false;
    }
  }
}
