package som.tests;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public final class ParallelHelper {

  public static void executeNTimesInParallel(final Runnable task) throws InterruptedException {
    int numThreads = Math.max(3, Runtime.getRuntime().availableProcessors());

    ExecutorService threadPool = Executors.newFixedThreadPool(numThreads);

    try {
      List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<Throwable>());

      CountDownLatch threadsInitialized = new CountDownLatch(numThreads);
      CountDownLatch threadsDone = new CountDownLatch(numThreads);

      for (int i = 0; i < numThreads; i++) {
        threadPool.submit(() -> {
          try {
            threadsInitialized.countDown();
            threadsInitialized.await();

            task.run();

            threadsDone.countDown();
          } catch (Throwable t) {
            exceptions.add(t);
          }
        });
      }

      assertTrue(threadsDone.await(10, TimeUnit.SECONDS));
      assertTrue("Failed parallel test with: " + exceptions, exceptions.isEmpty());
    } finally {
      threadPool.shutdownNow();
    }
  }
}
