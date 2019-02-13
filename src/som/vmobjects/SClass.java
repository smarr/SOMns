/**
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package som.vmobjects;

import java.util.ArrayList;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.ClassSlotDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import tools.concurrency.TracingActors.TracingActor;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.nodes.AbstractSerializationNode;
import tools.snapshot.nodes.MessageSerializationNode;


// TODO: should we move more of that out of SClass and use the corresponding
//       ClassFactory?
public final class SClass extends SObjectWithClass {

  @CompilationFinal private SClass  superclass;
  @CompilationFinal private SSymbol name;

  @CompilationFinal private EconomicMap<SSymbol, Dispatchable> dispatchables;

  // includes slots of super classes and mixins
  @CompilationFinal private EconomicSet<SlotDefinition> slots;

  @CompilationFinal private MixinDefinition mixinDef;
  @CompilationFinal private boolean         declaredAsValue;
  @CompilationFinal private boolean         isTransferObject; // is a kind of TransferObject
                                                              // (subclass or TObj directly)
  @CompilationFinal private boolean         isArray;          // is a subclass of Array

  @CompilationFinal private ClassFactory instanceClassGroup; // the factory for this object
  @CompilationFinal private int          identity;

  @CompilationFinal private TracingActor ownerOfOuter;

  protected final SObjectWithClass enclosingObject;
  private final MaterializedFrame  context;

  /**
   * The constructor used for instantiating empty classes and meta-classes
   * (these classes do not have an enclosing activation).
   */
  public SClass(final SObjectWithClass enclosing) {
    this.enclosingObject = enclosing;
    this.context = null;
  }

  /**
   * The constructor used for instantiating standard classes (these classes
   * do not have an enclosing activation).
   */
  public SClass(final SObjectWithClass enclosing, final SClass clazz) {
    super(clazz, clazz.getInstanceFactory());
    this.enclosingObject = enclosing;
    this.context = null;
  }

  /**
   * The constructor used for instantiating the SClass of an object literal.
   *
   * @param frame, the current activation.
   */
  public SClass(final SObjectWithClass enclosing, final SClass clazz,
      final MaterializedFrame frame) {
    super(clazz, clazz.getInstanceFactory());
    this.enclosingObject = enclosing;
    this.context = frame;
  }

  public SObjectWithClass getEnclosingObject() {
    return enclosingObject;
  }

  public SClass getSuperClass() {
    return superclass;
  }

  public ClassFactory getInstanceFactory() {
    // assert classGroup != null || !ObjectSystem.isInitialized();
    // assert instanceClassGroup != null || !ObjectSystem.isInitialized();
    // assert classGroup != instanceClassGroup || !ObjectSystem.isInitialized();
    return instanceClassGroup;
  }

  public ObjectLayout getLayoutForInstancesUnsafe() {
    return instanceClassGroup.getInstanceLayout();
  }

  public ObjectLayout getLayoutForInstancesToUpdateObject() {
    ObjectLayout layout = instanceClassGroup.getInstanceLayout();

    // layout might already be invalidated, let's busy wait here
    //
    // Some class loading might happen when initializing a new object layout
    // (new ObjectLayout being called by another thread doing a layout transition).
    // Class loading can take considerable time and might be problematic here.
    // But seems better than running into a stack overflow in other places.
    while (!layout.isValid()) {
      // TODO(JDK9): add call to Thread.onSpinWait() once moving to JDK9 support
      layout = instanceClassGroup.getInstanceLayout();
    }

    return layout;
  }

  public EconomicSet<SlotDefinition> getInstanceSlots() {
    return slots;
  }

  public void initializeClass(final SSymbol name, final SClass superclass) {
    assert (this.name == null || this.name == name) && (this.superclass == null
        || this.superclass == superclass) : "Should only be initialized once";
    this.name = name;
    this.superclass = superclass;
  }

  public SSymbol getName() {
    return name;
  }

  public boolean declaredAsValue() {
    return declaredAsValue;
  }

  public boolean isTransferObject() {
    return isTransferObject;
  }

  public boolean isArray() {
    return isArray;
  }

  @Override
  public boolean isValue() {
    return enclosingObject.isValue();
  }

  public MixinDefinition getMixinDefinition() {
    return mixinDef;
  }

  public void initializeStructure(final MixinDefinition mixinDef,
      final EconomicSet<SlotDefinition> slots,
      final EconomicMap<SSymbol, Dispatchable> dispatchables,
      final boolean declaredAsValue, final boolean isTransferObject,
      final boolean isArray,
      final ClassFactory classFactory) {
    assert slots == null || slots.size() > 0;

    this.mixinDef = mixinDef;
    this.slots = slots;
    this.dispatchables = dispatchables;
    this.declaredAsValue = declaredAsValue;
    this.isTransferObject = isTransferObject;
    this.isArray = isArray;
    this.instanceClassGroup = classFactory;

    if (VmSettings.SNAPSHOTS_ENABLED) {
      identity = instanceClassGroup.createIdentity();

      if (!VmSettings.REPLAY && !VmSettings.TEST_SNAPSHOTS && enclosingObject != null) {

        if (Thread.currentThread() instanceof ActorProcessingThread) {
          this.ownerOfOuter =
              (TracingActor) ((ActorProcessingThread) Thread.currentThread()).getCurrentActor();
        }
      }
    }
    // assert instanceClassGroup != null || !ObjectSystem.isInitialized();

    if (VmSettings.TRACK_SNAPSHOT_ENTITIES) {
      SnapshotBackend.registerClass(this);
    }
  }

  public TracingActor getOwnerOfOuter() {
    return ownerOfOuter;
  }

  public void customizeSerializerFactory(
      final NodeFactory<? extends AbstractSerializationNode> factory,
      final AbstractSerializationNode deserializer) {
    instanceClassGroup.customizeSerialization(factory, deserializer);
  }

  public NodeFactory<? extends AbstractSerializationNode> getSerializerFactory() {
    return instanceClassGroup.getSerializerFactory();
  }

  /**
   * This method checks whether a given class was created from `this`
   * class. The check is made by comparing unique identifiers, the
   * one given as an argument and the from the MixinDefintion encapsulated
   * by this class. The identities match when the class identifier by the
   * argument was created by this class.
   */
  public boolean isBasedOn(final MixinDefinitionId mixinId) {
    return this.mixinDef.getMixinId() == mixinId;
  }

  /**
   * Checks whether the classes are the same, including superclass hierarchy
   * and ignoring class identity, i.e., relying on class groups/factories, too.
   */
  public boolean isKindOf(final SClass clazz) {
    VM.callerNeedsToBeOptimized("This method is not optimized for run-time performance.");
    if (this == clazz) {
      return true;
    }
    if (this == Classes.topClass) {
      return false;
    }
    if (this.instanceClassGroup == clazz.instanceClassGroup) {
      return true;
    }
    return superclass.isKindOf(clazz);
  }

  public SClass lookupClass(final MixinDefinitionId mixinId) {
    VM.callerNeedsToBeOptimized(
        "This should not be on the fast path, specialization/caching needed?");
    SClass cls = this;
    while (cls != null && !cls.isBasedOn(mixinId)) {
      cls = cls.getSuperClass();
    }
    return cls;
  }

  public SClass lookupClassWithMixinApplied(final MixinDefinitionId mixinId) {
    VM.callerNeedsToBeOptimized(
        "This should not be on the fast path, specialization/caching needed?");
    SClass cls = this;
    while (cls != null && !instanceClassGroup.isBasedOn(mixinId)) {
      cls = cls.getSuperClass();
    }
    return cls;
  }

  @ExplodeLoop
  public SClass lookupClass(final int superclassIdx) {
    SClass cls = this;
    for (int i = 0; i < superclassIdx && cls != null; i++) {
      cls = cls.getSuperClass();
    }
    return cls;
  }

  public int getIdxForClassCorrespondingTo(final MixinDefinitionId mixinId) {
    VM.callerNeedsToBeOptimized(
        "This should not be on the fast path, specialization/caching needed?");
    SClass cls = this;
    int i = 0;
    while (cls != null && !cls.isBasedOn(mixinId)) {
      cls = cls.getSuperClass();
      i++;
    }
    return i;
  }

  public boolean canUnderstand(final SSymbol selector) {
    VM.callerNeedsToBeOptimized("Should not be called on fast path");
    return dispatchables.containsKey(selector);
  }

  @TruffleBoundary
  public SInvokable[] getMethods() {
    ArrayList<SInvokable> methods = new ArrayList<SInvokable>();
    for (Dispatchable disp : dispatchables.getValues()) {
      if (disp instanceof SInvokable) {
        methods.add((SInvokable) disp);
      }
    }
    return methods.toArray(new SInvokable[methods.size()]);
  }

  @TruffleBoundary
  public SClass[] getNestedClasses(final SObjectWithClass instance) {
    VM.thisMethodNeedsToBeOptimized("Not optimized, we do unrecorded invokes here");
    ArrayList<SClass> classes = new ArrayList<SClass>();
    for (Dispatchable disp : dispatchables.getValues()) {
      if (disp instanceof ClassSlotDefinition) {
        classes.add((SClass) disp.invoke(null, new Object[] {instance}));
      }
    }
    return classes.toArray(new SClass[classes.size()]);
  }

  public EconomicMap<SSymbol, Dispatchable> getDispatchables() {
    return dispatchables;
  }

  public Dispatchable lookupPrivate(final SSymbol selector,
      final MixinDefinitionId mixinId) {
    VM.callerNeedsToBeOptimized("should never be called on fast path");

    SClass cls = lookupClass(mixinId);
    if (cls != null) {
      Dispatchable disp = cls.dispatchables.get(selector);
      if (disp != null && disp.getAccessModifier() == AccessModifier.PRIVATE) {
        return disp;
      }
    }
    return lookupMessage(selector, AccessModifier.PROTECTED);
  }

  /**
   * Find the dispatchable for the given selector symbol.
   *
   * @param selector to be used for lookup
   * @param hasAtLeast the minimal access level the found method/slot-accessor
   *          is allowed to have
   * @return a method or slot accessor
   */
  public Dispatchable lookupMessage(final SSymbol selector,
      final AccessModifier hasAtLeast) {
    assert hasAtLeast.ordinal() >= AccessModifier.PROTECTED.ordinal() : "Access modifier should be protected or public";
    VM.callerNeedsToBeOptimized("should never be called on fast path");

    Dispatchable disp = dispatchables.get(selector);

    if (disp != null && disp.getAccessModifier().ordinal() >= hasAtLeast.ordinal()) {
      return disp;
    }

    if (superclass == Classes.topClass) {
      return null;
    } else {
      return superclass.lookupMessage(selector, hasAtLeast);
    }
  }

  @Override
  public String toString() {
    return "Class(" + getName().getString() + ")";
  }

  @Override
  public MaterializedFrame getContext() {
    return context;
  }

  public void serialize(final Object o, final SnapshotBuffer sb) {
    assert instanceClassGroup != null;
    if (!sb.getRecord().containsObjectUnsync(o)) {
      instanceClassGroup.serialize(o, sb);
    }
  }

  public Object deserialize(final DeserializationBuffer bb) {
    if (this == Classes.messageClass) {
      return MessageSerializationNode.deserializeMessage(bb);
    }

    return this.instanceClassGroup.deserialize(bb, this);
  }

  public int getIdentity() {
    assert identity != 0;
    return identity;
  }
}
