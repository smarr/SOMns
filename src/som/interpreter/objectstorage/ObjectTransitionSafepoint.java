package som.interpreter.objectstorage;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.compiler.MixinDefinition.SlotDefinition;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;


/**
 * Implements a global safepoint to transition objects from outdated layouts
 * to the latest layout safely.
 *
 * <p>
 * These safepoints are necessary for the thread-safe dynamic object model
 * implemented here.
 *
 * <p>
 * Inspired by: Techniques and Applications for Guest-Language Safepoints.
 * B. Daloze, C. Seaton, D. Bonetta, H. Mössenböck. In Proc. of ICOOOLPS, 2015.
 * DOI: 10.1145/2843915.2843921
 */
public final class ObjectTransitionSafepoint {
  @CompilationFinal private SafepointPhaser phaser;
  @CompilationFinal private Assumption      noSafePoint;

  private ObjectTransitionSafepoint() {
    phaser = new SafepointPhaser(this);
    noSafePoint = create();
  }

  /**
   * Only to be used in tests.
   */
  public static void reset() {
    INSTANCE.phaser = new SafepointPhaser(INSTANCE);
  }

  private static Assumption create() {
    return Truffle.getRuntime().createAssumption("Object Transition SafePoint");
  }

  /**
   * Registers a thread on the safepoint.
   *
   * <p>
   * Needs to be called by all threads that interact with Smalltalk objects
   * in some way. Thus, all threads that access {@link SMutableObject} or
   * {@link SImmutableObject} at some point of their lifetime need to register.
   */
  @TruffleBoundary
  public void register() {
    phaser.register();
  }

  /**
   * Unregisters a thread from the safepoint.
   *
   * <p>
   * Needs to be called by all threads that interact with Smalltalk objects
   * in some way. Thus, all threads that access {@link SMutableObject} or
   * {@link SImmutableObject} at some point of their lifetime need to register.
   */
  @TruffleBoundary
  public void unregister() {
    phaser.arriveAndDeregister();
  }

  /**
   * Check whether a safepoint needs to be performed.
   */
  public void checkAndPerformSafepoint() {
    try {
      noSafePoint.check();
    } catch (InvalidAssumptionException e) {
      phaser.performSafepoint();
    }
  }

  /**
   * Transition the given object to the latest layout.
   *
   * <p>
   * This method is racy, i.e., it can be called by multiple threads for the
   * same safepoint. It can be for multiple objects or the same.
   *
   * @param obj to be transitioned.
   */
  public void transitionObject(final SObject obj) {
    waitForSafepointStart();

    // Safepoint phase, used to update the object
    // object is required to handle updates from multiple threads correctly
    obj.updateLayoutToMatchClass();

    phaser.finishSafepointAndAwaitCompletion();
  }

  /**
   * Write uninitialized slot of the object and update its layout.
   *
   * <p>
   * This method is racy, i.e., it can be called by multiple threads for the
   * same safepoint. It can be for multiple objects or the same.
   */
  public void writeUninitializedSlot(final SObject obj, final SlotDefinition slot,
      final Object value) {
    waitForSafepointStart();

    // Safepoint phase, used to update the object
    // object is required to handle updates from multiple threads correctly
    obj.writeUninitializedSlot(slot, value);

    phaser.finishSafepointAndAwaitCompletion();
  }

  /**
   * Write a slot of the object and generalize its layout.
   *
   * <p>
   * This method is racy, i.e., it can be called by multiple threads for the
   * same safepoint. It can be for multiple objects or the same.
   */
  public void writeAndGeneralizeSlot(final SObject obj, final SlotDefinition slot,
      final Object value) {
    waitForSafepointStart();

    // Safepoint phase, used to update the object
    // object is required to handle updates from multiple threads correctly
    obj.writeAndGeneralizeSlot(slot, value);

    phaser.finishSafepointAndAwaitCompletion();
  }

  public void ensureSlotAllocatedToAvoidDeadlock(final SObject obj,
      final SlotDefinition slot) {
    waitForSafepointStart();

    // Safepoint phase, used to update the object
    // object is required to handle updates from multiple threads correctly
    obj.ensureSlotAllocatedToAvoidDeadlock(slot);

    phaser.finishSafepointAndAwaitCompletion();
  }

  void renewAssumption() {
    if (!noSafePoint.isValid()) {
      noSafePoint = create();
    }
  }

  private void waitForSafepointStart() {
    // Note: The whole Safepoint is in the interpreter, so, the trigger can be too
    CompilerAsserts.neverPartOfCompilation(
        "Compilation not supported, expect to be in non-PEed code.");

    // Ask all other threads to join in the safepoint
    noSafePoint.invalidate();

    phaser.arriveAtSafepointAndAwaitStart();
  }

  public static final ObjectTransitionSafepoint INSTANCE = new ObjectTransitionSafepoint();
}
