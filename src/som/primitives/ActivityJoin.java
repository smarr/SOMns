package som.primitives;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import som.vm.VmSettings;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.Tags.ExpressionBreakpoint;


public class ActivityJoin {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingTaskJoin:")
  @Primitive(primitive = "threadingThreadJoin:")
  @Primitive(selector = "join")
  public abstract static class JoinPrim extends UnaryExpressionNode {
    public JoinPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    @TruffleBoundary
    public final Object doTask(final SomForkJoinTask task) {
      Object result = task.join();

      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.taskJoin(task.getMethod(), task.getId());
      }
      return result;
    }

    @Specialization
    public final Object doThread(final Thread thread) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        /* ignore for the moment */
      }
      return thread;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == ActivityJoin.class || tag == ExpressionBreakpoint.class) {
        return true;
      }
      return super.isTaggedWith(tag);
    }
  }
}
