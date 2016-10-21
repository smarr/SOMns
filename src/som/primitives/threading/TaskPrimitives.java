package som.primitives.threading;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.VmSettings;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.primitives.arrays.ToArgumentsArrayNode;
import som.primitives.arrays.ToArgumentsArrayNodeFactory;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;

public final class TaskPrimitives {
  public static class SomForkJoinTask extends RecursiveTask<Object> {
    private static final long serialVersionUID = -2145613708553535622L;

    private final Object[] argArray;

    SomForkJoinTask(final Object[] argArray) {
      this.argArray = argArray;
      assert argArray[0] instanceof SBlock : "First argument of a block needs to be the block object";
    }

    @Override
    protected Object compute() {
      return ((SBlock) argArray[0]).getMethod().getCallTarget().call(argArray);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingTaskJoin:")
  public abstract static class JoinPrim extends UnaryExpressionNode {
    public JoinPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    @TruffleBoundary
    public final Object doTask(final SomForkJoinTask task) {
      return task.join();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingTaskSpawn:")
  public abstract static class SpawnPrim extends UnaryExpressionNode {
    public SpawnPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    @TruffleBoundary
    public final SomForkJoinTask doSBlock(final SBlock block) {
      SomForkJoinTask task = new SomForkJoinTask(new Object[] {block});
      forkJoinPool.execute(task);
      return task;
    }
  }

  @GenerateNodeFactory
  @NodeChild(value = "argArr", type = ToArgumentsArrayNode.class,
    executeWith = {"argument", "receiver"})
  @Primitive(primitive = "threadingTaskSpawn:with:", extraChild = ToArgumentsArrayNodeFactory.class)
  public abstract static class SpawnWithPrim extends BinaryExpressionNode {
    public SpawnWithPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    public SomForkJoinTask doSBlock(final SBlock block, final SArray somArgArr,
        final Object[] argArr) {
      SomForkJoinTask task = new SomForkJoinTask(argArr);
      forkJoinPool.execute(task);
      return task;
    }
  }

  private static final ForkJoinPool forkJoinPool = new ForkJoinPool(VmSettings.NUM_THREADS);
}
