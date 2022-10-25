package tools.debugger.message;
import com.oracle.truffle.api.debug.DebugValue;

import org.java_websocket.WebSocket;
import tools.debugger.FrontendConnector;
import tools.debugger.frontend.ApplicationThreadTask;
import tools.debugger.frontend.Suspension;
import com.oracle.truffle.api.debug.DebugStackFrame;

import java.util.Iterator;

public class EvaluateExpressionRequest extends Message.IncommingMessage {

    private class EvaluateExpressionTask extends ApplicationThreadTask {

        private final FrontendConnector frontend;
        private final Suspension suspension;
        private final int frameID;
        private final String expression;
        public Boolean executed = false;
        public DebugValue resultValue;

        EvaluateExpressionTask(final FrontendConnector frontend, final Suspension suspension, int frameID, String expression) {
            this.frontend = frontend;
            this.suspension = suspension;
            this.frameID = frameID;
            this.expression = expression;

        }

        @Override
        protected boolean execute() {
            Iterator<DebugStackFrame> it =frontend.getSuspension(0).getEvent().getStackFrames().iterator();
            DebugStackFrame frame = null;
                for(int i=0;i<=frameID;i++){
                    frame = it.next();
                }
            resultValue = frame.eval(expression);
            executed = true;
            return true;
        }
    }

    private String expression;
    private int frameId;

    @Override
    public void process(FrontendConnector connector, WebSocket conn) {
//       Suspension suspension = connector.getSuspension(0);
//       EvaluateExpressionTask task = new EvaluateExpressionTask(connector,suspension,frameId,expression);
//       suspension.submitTask(task);
//       int a = 0;
//       while (!task.executed){
//            a = a +1;
//       }
//       DebugValue value = task.resultValue;
    }
}
