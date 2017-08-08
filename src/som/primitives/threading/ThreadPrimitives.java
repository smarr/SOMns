package som.primitives.threading;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.primitives.threading.TaskThreads.SomThreadTask;
import som.vm.Activity;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import tools.concurrency.TracingActivityThread;


public final class ThreadPrimitives {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingName:")
  public abstract static class NamePrim extends UnaryExpressionNode {
    @Specialization
    public final Object doThread(final SomThreadTask thread) {
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
    @Specialization
    public final Object doThread(final SomThreadTask thread, final String name) {
      thread.setName(name);
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingCurrent:")
  public abstract static class CurrentPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSClass(final SClass module) {
      Activity activity = TracingActivityThread.currentThread().getActivity();
      if (activity instanceof SomThreadTask) {
        return activity;
      } else {
        return Nil.nilObject;
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingYieldCurrent:")
  public abstract static class YieldPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass doSClass(final SClass module) {
      Thread.yield();
      return module;
    }
  }
}
