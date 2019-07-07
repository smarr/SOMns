package som.primitives;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import som.interpreter.actors.SuspendExecutionNodeGen;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.threading.TaskThreads.SomTaskOrThread;
import som.vm.VmSettings;
import tools.concurrency.KomposTrace;
import tools.concurrency.Tags;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.debugger.entities.ReceiveOp;


public class ActivityJoin {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingTaskJoin:")
  @Primitive(primitive = "threadingThreadJoin:")
  @Primitive(selector = "join")
  public abstract static class JoinPrim extends UnaryExpressionNode {
    @Child protected UnaryExpressionNode haltNode;

    @Override
    @SuppressWarnings("unchecked")
    public final JoinPrim initialize(final SourceSection source) {
      super.initialize(source);
      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        haltNode = insert(SuspendExecutionNodeGen.create(0, null).initialize(source));
      }
      return this;
    }

    @TruffleBoundary
    private static Object doJoin(final SomTaskOrThread task) {
      try {
        ObjectTransitionSafepoint.INSTANCE.unregister();
        return task.join();
      } finally {
        ObjectTransitionSafepoint.INSTANCE.register();
      }
    }

    @Specialization
    public final Object doTask(final VirtualFrame frame, final SomTaskOrThread task) {
      Object result = doJoin(task);

      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED && task.stopOnJoin()) {
        haltNode.executeEvaluated(frame, result);
      }

      if (VmSettings.KOMPOS_TRACING) {
        KomposTrace.receiveOperation(ReceiveOp.TASK_JOIN, task.getId());
      }

      return result;
    }

    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == Tags.ActivityJoin.class || tag == ExpressionBreakpoint.class) {
        return true;
      }
      return super.hasTag(tag);
    }
  }
}
