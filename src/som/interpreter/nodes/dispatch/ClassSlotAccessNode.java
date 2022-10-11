package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.Invokable;
import som.interpreter.SArguments;
import som.interpreter.nodes.InstantiationNode.ClassInstantiationNode;
import som.interpreter.nodes.InstantiationNodeFactory.ClassInstantiationNodeGen;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.VmSettings;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import tools.debugger.asyncstacktraces.ShadowStackEntry;


/**
 * Similar to {@link CachedSlotRead field reads}, access to class objects
 * is realized as a simple message send in Newspeak, and as a result, part of
 * the dispatch chain.
 *
 * <p>
 * The Newspeak semantics defines that class objects are allocated lazily.
 * This is realized here using read/write nodes on a slot of the enclosing
 * object, where the initialized class object is cached.
 */
public final class ClassSlotAccessNode extends CachedSlotRead {
  private final MixinDefinition mixinDef;
  private final SlotDefinition  slotDef;

  @CompilationFinal private boolean objectSlotIsAllocated;

  @Child protected DirectCallNode         superclassAndMixinResolver;
  @Child protected ClassInstantiationNode instantiation;

  @Child protected CachedSlotRead  read;
  @Child protected CachedSlotWrite write;

  public ClassSlotAccessNode(final MixinDefinition mixinDef, final SlotDefinition slotDef,
      final CachedSlotRead read, final CachedSlotWrite write) {
    super(SlotAccess.CLASS_READ, read.guard, read.nextInCache);

    // TODO: can the slot read be an unwritten read? I'd think so.
    this.read = read;
    this.write = write;
    this.mixinDef = mixinDef;
    this.slotDef = slotDef;
    this.instantiation = ClassInstantiationNodeGen.create(mixinDef);
    this.objectSlotIsAllocated = guard.isObjectSlotAllocated(slotDef);
  }

  @Override
  public SClass read(final VirtualFrame frame, final SObject rcvr) {
    return this.read(frame, rcvr, null);
  }

  @Override
  public SClass read(final VirtualFrame frame, final SObject rcvr, final Object maybeEntry) {
    // here we need to synchronize, because this is actually something that
    // can happen concurrently, and we only want a single instance of the
    // class object
    Object cachedValue = read.read(frame, rcvr);

    assert cachedValue != null;
    if (cachedValue != Nil.nilObject) {
      return (SClass) cachedValue;
    }

    // NOTE: before going into the synchronized block, we make sure that the slot is
    // available in the object, it is allocated. Allocation may trigger a
    // safepoint and we can't hold a lock while going into a safepoint.
    // Thus, we make sure we do not trigger one.
    if (!objectSlotIsAllocated) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      ObjectTransitionSafepoint.INSTANCE.ensureSlotAllocatedToAvoidDeadlock(rcvr, slotDef);
      objectSlotIsAllocated = true;
    }

    synchronized (rcvr) {
      try {
        // recheck guard under synchronized, don't want to access object if
        // layout might have changed, we are going to slow path in that case
        guard.entryMatches(rcvr);
        cachedValue = read.read(frame, rcvr);
      } catch (InvalidAssumptionException e) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        cachedValue = rcvr.readSlot(slotDef);
      }

      // check whether cache is initialized with class object
      if (cachedValue == Nil.nilObject) {
        return instantiateAndWriteUnsynced(frame, rcvr, maybeEntry);
      } else {
        assert cachedValue instanceof SClass;
        return (SClass) cachedValue;
      }
    }
  }

  /**
   * Caller needs to hold lock on {@code this}.
   */
  private SClass instantiateAndWriteUnsynced(final VirtualFrame frame, final SObject rcvr,
      final Object maybeEntry) {
    SClass classObject = instantiateClassObject(frame, rcvr, maybeEntry);

    try {
      // recheck guard under synchronized, don't want to access object if
      // layout might have changed
      //
      // at this point the guard will fail, if it failed for the read guard,
      // but we simply recheck here to avoid impact on fast path
      guard.entryMatches(rcvr);
      write.doWrite(rcvr, classObject);
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      rcvr.writeSlot(slotDef, classObject);
    }
    return classObject;
  }

  private void createResolverCallTargets() {
    CompilerAsserts.neverPartOfCompilation();
    Invokable invokable = mixinDef.getSuperclassAndMixinResolutionInvokable();
    superclassAndMixinResolver = insert(Truffle.getRuntime().createDirectCallNode(
        invokable.getCallTarget()));
  }

  private SClass instantiateClassObject(final VirtualFrame frame, final SObject rcvr,
      final Object maybeEntry) {
    if (superclassAndMixinResolver == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      createResolverCallTargets();
    }

    Object superclassAndMixins;
    if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
      // maybeEntry is set if coming from a cached dispatch node
      // Not set if comes from elsewhere (materializer, etc.)..
      // But it may not make sense.
      ShadowStackEntry actualEntry;
      if (maybeEntry != null) {
        assert maybeEntry instanceof ShadowStackEntry;
        actualEntry = (ShadowStackEntry) maybeEntry;
      } else {
        actualEntry = SArguments.instantiateTopShadowStackEntry(this);
      }
      superclassAndMixins =
          superclassAndMixinResolver.call(
              new Object[] {rcvr, actualEntry});
    } else {
      superclassAndMixins = superclassAndMixinResolver.call(new Object[] {rcvr});
    }

    SClass classObject = instantiation.execute(frame, rcvr, superclassAndMixins);
    return classObject;
  }
}
