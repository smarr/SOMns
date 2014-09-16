package som.primitives;

import som.primitives.threads.WaitPrimFactory;



public final class DelayPrimitives extends Primitives {
  @Override
  public void installPrimitives() {
    installInstancePrimitive("wait", WaitPrimFactory.getInstance());
  }
}
