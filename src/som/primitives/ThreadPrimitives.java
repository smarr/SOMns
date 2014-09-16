package som.primitives;

import som.primitives.threads.ThreadPrimFactory.ThreadCurrentPrimFactory;
import som.primitives.threads.ThreadPrimFactory.ThreadJoinPrimFactory;
import som.primitives.threads.ThreadPrimFactory.ThreadNamePrimFactory;
import som.primitives.threads.ThreadPrimFactory.ThreadSetNamePrimFactory;
import som.primitives.threads.ThreadPrimFactory.ThreadYieldPrimFactory;


public class ThreadPrimitives extends Primitives {
  @Override
  public void installPrimitives() {
    installInstancePrimitive("name",  ThreadNamePrimFactory.getInstance());
    installInstancePrimitive("name:", ThreadSetNamePrimFactory.getInstance());
    installInstancePrimitive("join",  ThreadJoinPrimFactory.getInstance());

    installClassPrimitive("yield",   ThreadYieldPrimFactory.getInstance());
    installClassPrimitive("current", ThreadCurrentPrimFactory.getInstance());
  }
}
