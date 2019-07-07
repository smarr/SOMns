package som.interpreter.objectstorage;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.graalvm.collections.EconomicSet;
import org.junit.Test;

import som.compiler.AccessModifier;
import som.compiler.MixinDefinition.SlotDefinition;
import som.tests.ParallelHelper;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SMutableObject;


public class SafepointPhaserTest {

  private final SClass       instanceClass;
  private final ClassFactory factory;
  private final ObjectLayout layout;

  public SafepointPhaserTest() {
    SlotDefinition slotDef = new SlotDefinition(null, AccessModifier.PUBLIC, false, null);
    EconomicSet<SlotDefinition> instanceSlots = EconomicSet.create();
    instanceSlots.add(slotDef);

    instanceClass = new SClass(null);
    factory = new ClassFactory(null, null, instanceSlots, null, false, false, false, null,
        false, null, null);

    instanceClass.initializeStructure(null, null, null, false, false, false, factory);
    layout = factory.getInstanceLayout();
  }

  @Test
  public void testThreadsRegisterAndUnregister() throws InterruptedException {
    CyclicBarrier barrier = new CyclicBarrier(ParallelHelper.getNumberOfThreads());

    SafepointPhaser phaser = new SafepointPhaser(ObjectTransitionSafepoint.INSTANCE);
    ParallelHelper.executeNTimesInParallel((final int id) -> {

      phaser.register();

      try {
        barrier.await();
      } catch (InterruptedException | BrokenBarrierException e) {
        throw new RuntimeException(e);
      }

      phaser.arriveAndDeregister();
      return null;
    });

    assertEquals(phaser.getPhase(), 2);
  }

  @Test
  public void testThreadsRegisterTriggerSafepointAndUnregister() throws InterruptedException {
    CyclicBarrier barrier = new CyclicBarrier(ParallelHelper.getNumberOfThreads());

    ObjectTransitionSafepoint.reset();

    ParallelHelper.executeNTimesInParallel((final int id) -> {

      ObjectTransitionSafepoint.INSTANCE.register();

      try {
        barrier.await();
      } catch (InterruptedException | BrokenBarrierException e) {
        throw new RuntimeException(e);
      }

      ObjectTransitionSafepoint.INSTANCE.transitionObject(
          new SMutableObject(instanceClass, factory, layout));

      ObjectTransitionSafepoint.INSTANCE.unregister();
      return null;
    });
  }

  @Test
  public void testSafepointStorm() throws InterruptedException {
    ObjectTransitionSafepoint.reset();
    ParallelHelper.executeNTimesInParallel((final int id) -> {

      ObjectTransitionSafepoint.INSTANCE.register();

      for (int i = 0; i < 500_000; i += 1) {
        ObjectTransitionSafepoint.INSTANCE.transitionObject(
            new SMutableObject(instanceClass, factory, layout));
      }

      ObjectTransitionSafepoint.INSTANCE.unregister();
      return null;
    }, 60);
  }

  @Test
  public void testSingleSafepointStorm() throws InterruptedException {
    ObjectTransitionSafepoint.reset();
    ParallelHelper.executeNTimesInParallel((final int id) -> {

      ObjectTransitionSafepoint.INSTANCE.register();

      for (int i = 0; i < 100_000; i += 1) {
        if (id == 0) {
          ObjectTransitionSafepoint.INSTANCE.transitionObject(
              new SMutableObject(instanceClass, factory, layout));
        } else {
          ObjectTransitionSafepoint.INSTANCE.checkAndPerformSafepoint();
        }
      }

      ObjectTransitionSafepoint.INSTANCE.unregister();
      return null;
    }, 60);
  }

  @Test
  public void testSafepointRegisterStorm() throws InterruptedException {
    ObjectTransitionSafepoint.reset();
    ParallelHelper.executeNTimesInParallel((final int id) -> {
      for (int i = 0; i < 100_000; i += 1) {
        ObjectTransitionSafepoint.INSTANCE.register();

        ObjectTransitionSafepoint.INSTANCE.transitionObject(
            new SMutableObject(instanceClass, factory, layout));

        ObjectTransitionSafepoint.INSTANCE.unregister();
      }
      return null;
    }, 60);
  }

  @Test
  public void testSingleSafepointRegisterStorm() throws InterruptedException {
    ObjectTransitionSafepoint.reset();
    ParallelHelper.executeNTimesInParallel((final int id) -> {
      for (int i = 0; i < 100_000; i += 1) {
        ObjectTransitionSafepoint.INSTANCE.register();

        if (id == 0) {
          ObjectTransitionSafepoint.INSTANCE.transitionObject(
              new SMutableObject(instanceClass, factory, layout));
        } else {
          ObjectTransitionSafepoint.INSTANCE.checkAndPerformSafepoint();
        }

        ObjectTransitionSafepoint.INSTANCE.unregister();
      }
      return null;
    }, 60);
  }

  @Test
  public void testRegisterStorm() throws InterruptedException {
    ObjectTransitionSafepoint.reset();
    ParallelHelper.executeNTimesInParallel((final int id) -> {
      for (int i = 0; i < 100_000; i += 1) {
        ObjectTransitionSafepoint.INSTANCE.register();
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }
      return null;
    }, 60);
  }
}
