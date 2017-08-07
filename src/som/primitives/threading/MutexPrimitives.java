package som.primitives.threading;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import tools.concurrency.Tags.AcquireLock;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.concurrency.Tags.ReleaseLock;


public final class MutexPrimitives {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingLock:", selector = "lock")
  public abstract static class LockPrim extends UnaryExpressionNode {
    public LockPrim(final boolean ew, final SourceSection s) {
      super(ew, s);
    }

    @TruffleBoundary
    @Specialization
    public static final ReentrantLock lock(final ReentrantLock lock) {
      lock.lock();
      return lock;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == AcquireLock.class || tag == ExpressionBreakpoint.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingUnlock:", selector = "unlock")
  public abstract static class UnlockPrim extends UnaryExpressionNode {
    public UnlockPrim(final boolean ew, final SourceSection s) {
      super(ew, s);
    }

    @TruffleBoundary
    @Specialization
    public static final ReentrantLock unlock(final ReentrantLock lock) {
      lock.unlock();
      return lock;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == ReleaseLock.class || tag == ExpressionBreakpoint.class) {
        return true;
      } else {
        return super.isTaggedWithIgnoringEagerness(tag);
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(selector = "critical:", receiverType = ReentrantLock.class)
  public abstract static class CritialPrim extends BinaryExpressionNode {
    public CritialPrim(final boolean ew, final SourceSection s) {
      super(ew, s);
    }

    @Child protected BlockDispatchNode dispatchBody = BlockDispatchNodeGen.create();

    @Specialization
    public Object critical(final ReentrantLock lock, final SBlock block) {
      LockPrim.lock(lock);
      try {
        return dispatchBody.executeDispatch(new Object[] {block});
      } finally {
        UnlockPrim.unlock(lock);
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingIsLocked:")
  public abstract static class IsLockedPrim extends UnaryExpressionNode {
    public IsLockedPrim(final boolean ew, final SourceSection s) {
      super(ew, s);
    }

    @Specialization
    @TruffleBoundary
    public boolean doLock(final ReentrantLock lock) {
      return lock.isLocked();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingConditionFor:")
  public abstract static class ConditionForPrim extends UnaryExpressionNode {
    public ConditionForPrim(final boolean ew, final SourceSection s) {
      super(ew, s);
    }

    @Specialization
    @TruffleBoundary
    public Condition doLock(final ReentrantLock lock) {
      return lock.newCondition();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingMutexNew:")
  public abstract static class MutexNewPrim extends UnaryExpressionNode {
    public MutexNewPrim(final boolean ew, final SourceSection s) {
      super(ew, s);
    }

    // TODO: should I guard this on the mutex class?
    @Specialization
    @TruffleBoundary
    public final ReentrantLock doSClass(final SClass clazz) {
      return new ReentrantLock();
    }
  }
}
