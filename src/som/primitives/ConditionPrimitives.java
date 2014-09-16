package som.primitives;

import som.primitives.threads.ConditionPrimFactory.ConditionAwaitForPrimFactory;
import som.primitives.threads.ConditionPrimFactory.ConditionAwaitPrimFactory;
import som.primitives.threads.ConditionPrimFactory.ConditionSignalAllPrimFactory;
import som.primitives.threads.ConditionPrimFactory.ConditionSignalOnePrimFactory;


public final class ConditionPrimitives extends Primitives {
  @Override
  public void installPrimitives() {
    installInstancePrimitive("signalOne", ConditionSignalOnePrimFactory.getInstance());
    installInstancePrimitive("signalAll", ConditionSignalAllPrimFactory.getInstance());
    installInstancePrimitive("await",     ConditionAwaitPrimFactory.getInstance());
    installInstancePrimitive("await:",    ConditionAwaitForPrimFactory.getInstance());
  }
}
