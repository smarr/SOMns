package som.primitives.threads;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.constants.ThreadClasses;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


public final class MutexPrims {
  @GenerateNodeFactory
  public abstract static class NewMutexPrim extends UnaryExpressionNode {
    protected static final boolean isMutexClass(final SClass receiver) {
      return receiver == ThreadClasses.mutexClass;
    }

    @Specialization(guards = "isMutexClass(receiver)")
    public final ReentrantLock doNew(final SClass receiver) {
      return new ReentrantLock();
    }
  }

  @GenerateNodeFactory
  public abstract static class UnaryMutexPrim extends UnaryExpressionNode {
    public UnaryMutexPrim() { super(null); }
  }

  public abstract static class LockPrim extends UnaryMutexPrim {
    @Specialization
    public final ReentrantLock doSMutex(final ReentrantLock mutex) {
      mutex.lock();
      return mutex;
    }
  }

  public abstract static class UnlockPrim extends UnaryMutexPrim {
    @Specialization
    public final ReentrantLock doSMutex(final ReentrantLock mutex) {
      mutex.unlock();
      return mutex;
    }
  }

  public abstract static class IsLockedPrim extends UnaryMutexPrim {
    @Specialization
    public final boolean doSMutex(final ReentrantLock mutex) {
      return mutex.isLocked();
    }
  }

  public abstract static class NewConditionPrim extends UnaryMutexPrim {
    @Specialization
    public final Condition doSMutex(final ReentrantLock mutex) {
      return mutex.newCondition();
    }
  }
}
