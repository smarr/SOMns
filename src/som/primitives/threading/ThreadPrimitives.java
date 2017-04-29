package som.primitives.threading;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.Primitive;
import som.vm.Activity;
import som.vm.ActivityThread;
import som.vm.constants.Nil;
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

  public static final class SomThread extends Thread
      implements Activity, ActivityThread {
    private final Object[] args;
    private final SBlock block;

    public SomThread(final SBlock block, final Object... args) {
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

    @Override
    public Activity getActivity() {
      return this;
    }
  }
}
