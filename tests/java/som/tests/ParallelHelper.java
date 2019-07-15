package som.tests;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;


public final class ParallelHelper {

  public static void executeNTimesInParallel(final IntFunction<Void> task)
      throws InterruptedException {
    executeNTimesInParallel(task, 10);
  }

  public static void executeNTimesInParallel(final IntFunction<Void> task,
      final int timeoutInSeconds) throws InterruptedException {
    int numThreads = getNumberOfThreads();

    ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);

    try {
      List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());

      CountDownLatch threadsInitialized = new CountDownLatch(numThreads);
      CountDownLatch threadsDone = new CountDownLatch(numThreads);

      for (int i = 0; i < numThreads; i++) {
        int id = i;
        threadPool.submit(() -> {
          try {
            threadsInitialized.countDown();
            threadsInitialized.await();

            task.apply(id);
          } catch (Throwable t) {
            exceptions.add(t);
          }
          threadsDone.countDown();
        });
      }
      boolean allArrivedWithinTime = threadsDone.await(timeoutInSeconds, TimeUnit.SECONDS);
      if (!exceptions.isEmpty()) {
        for (Throwable e : exceptions) {
          e.printStackTrace();
        }
      }

      assertTrue("Failed parallel test with: " + exceptions, exceptions.isEmpty());
      assertTrue(allArrivedWithinTime);
    } finally {
      threadPool.shutdownNow();
    }
  }

  public static int getNumberOfThreads() {
    return Math.max(3, Runtime.getRuntime().availableProcessors());
  }
}
