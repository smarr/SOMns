package som.primitives.threads;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySideEffectFreeExpressionNode;
import som.vm.constants.Nil;
import som.vm.constants.ThreadClasses;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;


public final class ThreadPrim {
  public abstract static class ThreadNamePrim extends UnarySideEffectFreeExpressionNode {
    @Specialization
    public final Object doThread(final Thread receiver) {
      String name = receiver.getName();
      if (name == null) {
        return Nil.nilObject;
      } else {
        return name;
      }
    }
  }

  public abstract static class ThreadSetNamePrim extends BinaryExpressionNode {
    public ThreadSetNamePrim() { super(null); }
    @Specialization
    public final Thread doThread(final Thread receiver, final String name) {
      assert name != null;
      receiver.setName(name);
      return receiver;
    }
  }

  public abstract static class ThreadJoinPrim extends UnaryExpressionNode {
    public ThreadJoinPrim() { super(null); }
    @Specialization
    public final Thread doThread(final Thread receiver) {
      try {
        receiver.join();
      } catch (InterruptedException e) { /* not relevant at the moment */ }
      return receiver;
    }
  }


  public abstract static class ThreadYieldPrim extends UnaryExpressionNode {
    public ThreadYieldPrim() { super(null); }

    protected boolean isThreadClass(final SClass receiver) {
      return receiver == ThreadClasses.threadClass;
    }

    @Specialization(guards = "isThreadClass")
    public final SClass doThread(final SClass receiver) {
      Thread.yield();
      return receiver;
    }
  }

  public abstract static class ThreadCurrentPrim extends UnaryExpressionNode {
    public ThreadCurrentPrim() { super(null); }

    protected boolean isThreadClass(final SClass receiver) {
      return receiver == ThreadClasses.threadClass;
    }

    @Specialization
    public final Thread doThread(final SClass receiver) {
      return Thread.currentThread();
    }
  }
}
