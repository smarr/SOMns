package som.interpreter.objectstorage;

import java.util.HashMap;
import java.util.HashSet;

import com.oracle.truffle.api.CompilerAsserts;

import som.VM;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;


/**
 * A ClassFactory creates SClass objects based on a concrete super class
 * and a specific set of mixins.
 */
public final class ClassFactory {

  // TODO: the initializer, and value class check node, can be simplified, it only needs to be
  // added when the flag is set
  // because now with the ClassBuilder approach, we cache the result

  //
  //
  // should the classBuilder keep a layout for all related instances?
  // we could create a special slot-access dispatch node in the message dispatch chain, that
  // will only check the layout, not the class
  //
  // the problem there would be that we probably can't remove old layouts from chains, as we do
  // now
  // the benefit is that we can already during creation use the layout, and
  // reuse it
  // and the logic to determine whether all slots are immutable
  // and all new objects always use the initialized shapes
  //
  // identity criteria
  private final SClass[] superclassAndMixins;
  private final boolean  isDeclaredAsValue;
  private final boolean  isTransferObject;
  private final boolean  isArray;

  // properties of this group of classes
  private final SSymbol className;

  private final MixinDefinition mixinDef;

  private final HashSet<SlotDefinition>        instanceSlots;
  private final HashMap<SSymbol, Dispatchable> dispatchables;

  private final boolean hasOnlyImmutableFields;

  private volatile ObjectLayout instanceLayout;

  private final ClassFactory classClassFactory;

  public ClassFactory(final SSymbol name, final MixinDefinition mixinDef,
      final HashSet<SlotDefinition> instanceSlots,
      final HashMap<SSymbol, Dispatchable> dispatchables,
      final boolean declaredAsValue,
      final boolean isTransferObject,
      final boolean isArray,
      final SClass[] superclassAndMixins,
      final boolean hasOnlyImmutableFields,
      final ClassFactory classClassFactory) {
    assert instanceSlots == null || instanceSlots.size() > 0;

    this.className = name;
    this.mixinDef = mixinDef;
    this.instanceSlots = instanceSlots;
    this.dispatchables = dispatchables;
    this.isDeclaredAsValue = declaredAsValue;
    this.isTransferObject = isTransferObject;
    this.isArray = isArray;

    this.hasOnlyImmutableFields = hasOnlyImmutableFields;

    this.superclassAndMixins = superclassAndMixins;

    VM.callerNeedsToBeOptimized(
        "instanceLayout should only be accessed on slow path. (and ClassFactory should only be instantiated on slowpath, too)");
    this.instanceLayout = (instanceSlots == null) ? null
        : new ObjectLayout(instanceSlots, this, isTransferObject);

    this.classClassFactory = classClassFactory;
  }

  public boolean isDeclaredAsValue() {
    return isDeclaredAsValue;
  }

  public boolean isTransferObject() {
    return isTransferObject;
  }

  public SSymbol getClassName() {
    return className;
  }

  public ClassFactory getClassClassFactory() {
    return classClassFactory;
  }

  public ObjectLayout getInstanceLayout() {
    VM.callerNeedsToBeOptimized("Should not be called on fast path");
    return instanceLayout;
  }

  public boolean hasSlots() {
    return instanceSlots != null;
  }

  public boolean hasOnlyImmutableFields() {
    return hasOnlyImmutableFields;
  }

  public SClass[] getSuperclassAndMixins() {
    return superclassAndMixins;
  }

  /**
   * This method is used to verify whether the class identified by `mixinId` was
   * created from either the superclasses or any of other mixins encapsulated by
   * this class factory.
   */
  public boolean isBasedOn(final MixinDefinitionId mixinId) {
    VM.callerNeedsToBeOptimized("Result of isBasedOn should be cached");
    for (SClass cls : getSuperclassAndMixins()) {
      if (cls.isBasedOn(mixinId)) {
        return true;
      }
    }
    return false;
  }

  public void initializeClass(final SClass result) {
    result.initializeClass(className, superclassAndMixins[0]);
    result.initializeStructure(mixinDef, instanceSlots,
        dispatchables, isDeclaredAsValue, isTransferObject, isArray, this);
  }

  public synchronized ObjectLayout updateInstanceLayoutWithInitializedField(
      final SlotDefinition slot, final Class<?> type) {
    CompilerAsserts.neverPartOfCompilation(
        "update instance layout with init field in class factory");
    ObjectLayout updated = instanceLayout.withInitializedField(slot, type);

    if (updated != instanceLayout) {
      instanceLayout = updated;
    }
    return instanceLayout;
  }

  public synchronized ObjectLayout updateInstanceLayoutWithGeneralizedField(
      final SlotDefinition slot) {
    CompilerAsserts.neverPartOfCompilation(
        "update instance layout with generalized field in class factory");
    ObjectLayout updated = instanceLayout.withGeneralizedField(slot);

    if (updated != instanceLayout) {
      instanceLayout = updated;
    }
    return instanceLayout;
  }

  @Override
  public String toString() {
    String s = "";
    for (SClass sc : superclassAndMixins) {
      s += ", " + sc.getName().getString();
    }
    return "ClsFct[" + className.getString() + s + "]";
  }
}
