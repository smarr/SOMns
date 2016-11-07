package som.primitives.threading;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.Primitive;
import som.primitives.arrays.ToArgumentsArrayNode;
import som.primitives.arrays.ToArgumentsArrayNodeFactory;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;

public final class ThreadPrimitives {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingName:")
  public abstract static class NamePrim extends UnaryExpressionNode {
    public NamePrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    public final Object doThread(final Thread thread) {
      String name = thread.getName();
      if (name == null) {
        return Nil.nilObject;
      } else {
        return name;
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingName:set:")
  public abstract static class NameSetPrim extends BinaryExpressionNode {
    public NameSetPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    public final Object doThread(final Thread thread, final String name) {
      thread.setName(name);
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingThreadJoin:")
  public abstract static class JoinPrim extends UnaryExpressionNode {
    public JoinPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    public final Object doThread(final Thread thread) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        /* ignore for the moment */
      }
      return thread;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingThreadSpawn:")
  public abstract static class SpawnPrim extends UnaryExpressionNode {
    public SpawnPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    public final Thread doSBlock(final SBlock block) {
      SomThread thread = new SomThread(block, block);
      thread.start();
      return thread;
    }
  }

  @GenerateNodeFactory
  @NodeChild(value = "argArr", type = ToArgumentsArrayNode.class,
    executeWith = {"argument", "receiver"})
  @Primitive(primitive = "threadingThreadSpawn:with:", extraChild = ToArgumentsArrayNodeFactory.class)
  public abstract static class SpawnWithPrim extends BinaryExpressionNode {
    public SpawnWithPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    public Thread doSBlock(final SBlock block, final SArray somArgArr,
        final Object[] argArr) {
      SomThread thread = new SomThread(block, argArr);
      thread.start();
      return thread;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingCurrent:")
  public abstract static class CurrentPrim extends UnaryExpressionNode {
    public CurrentPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    public final Thread doSClass(final SClass module) {
      return Thread.currentThread();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingYieldCurrent:")
  public abstract static class YieldPrim extends UnaryExpressionNode {
    public YieldPrim(final boolean ew, final SourceSection s) { super(ew, s); }

    @Specialization
    public final SClass doSClass(final SClass module) {
      Thread.yield();
      return module;
    }
  }

  private static final class SomThread extends Thread {
    private final Object[] args;
    private final SBlock block;

    SomThread(final SBlock block, final Object... args) {
      this.block = block;
      this.args  = args;
    }

    @Override
    public void run() {
      ObjectTransitionSafepoint.INSTANCE.register();
      try {
        block.getMethod().getCallTarget().call(args);
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }
    }
  }
}
