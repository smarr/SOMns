package som.primitives;

import som.primitives.threads.WaitPrimFactory;


public final class DelayPrimitives extends Primitives {
  public DelayPrimitives(final boolean displayWarning) { super(displayWarning); }

  @Override
  public void installPrimitives() {
    installInstancePrimitive("wait", WaitPrimFactory.getInstance());
  }
}
