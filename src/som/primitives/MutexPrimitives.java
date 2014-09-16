package som.primitives;

import som.primitives.threads.MutexPrimsFactory.IsLockedPrimFactory;
import som.primitives.threads.MutexPrimsFactory.LockPrimFactory;
import som.primitives.threads.MutexPrimsFactory.NewConditionPrimFactory;
import som.primitives.threads.MutexPrimsFactory.NewMutexPrimFactory;
import som.primitives.threads.MutexPrimsFactory.UnlockPrimFactory;


public final class MutexPrimitives extends Primitives {
  @Override
  public void installPrimitives() {
    installInstancePrimitive("lock",         LockPrimFactory.getInstance());
    installInstancePrimitive("unlock",       UnlockPrimFactory.getInstance());
    installInstancePrimitive("isLocked",     IsLockedPrimFactory.getInstance());
    installInstancePrimitive("newCondition", NewConditionPrimFactory.getInstance());

    installClassPrimitive("new", NewMutexPrimFactory.getInstance());
  }
}
