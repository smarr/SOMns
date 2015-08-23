package som.interpreter.objectstorage;

import java.util.HashMap;
import java.util.HashSet;

import som.VM;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.LexicalScope.MixinScope;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;


/**
 * A ClassFactory creates instances for classes with a concrete super class
 * and a specific set of mixins.
 */
public final class ClassFactory {

  // TODO: the initializer, and value class check node, can be simplified, it only needs to be added when the flag is set
  //       because now with the ClassBuilder approach, we cache the result

//
//
//  should the classBuilder keep a layout for all related instances?
//  we could create a special slot-access dispatch node in the message dispatch chain, that will only check the layout, not the class
//
//      the problem there would be that we probably can't remove old layouts from chains, as we do now
//      the benefit is that we can already during createion use the layout, and
//      reuse it
//      and the logic to determine whether all slots are immutable
//      and all new objects always use the initialized shapes
//
  // identity criteria
  private final SClass[] superclassAndMixins;
  private final boolean  isValueClass;

  // properties of this group of classes
  private final SSymbol className;
  private final SSymbol classClassName;

  private final MixinScope      classScope;
  private final MixinDefinition mixinDef;

  private final HashSet<SlotDefinition>        instanceSlots;
  private final HashMap<SSymbol, Dispatchable> dispatchables;

  private final boolean isModule;
  private final boolean hasOnlyImmutableFields;

  // TODO: does it make sense to make it compilation final?
  //       think, it should only be accessed on the slow path
  private ObjectLayout instanceLayout;

  public ClassFactory(final SSymbol name, final MixinScope classScope,
      final MixinDefinition mixinDef, final HashSet<SlotDefinition> instanceSlots,
      final HashMap<SSymbol, Dispatchable> dispatchables, final boolean isModule,
      final SClass[] superclassAndMixins, final boolean hasOnlyImmutableFields,
      final boolean isValueClass) {
    assert instanceSlots == null || instanceSlots.size() > 0;

    className       = name;
    classClassName  = Symbols.symbolFor(name.getString() + " class");
    this.classScope = classScope;
    this.mixinDef   = mixinDef;
    this.instanceSlots = instanceSlots;
    this.dispatchables = dispatchables;
    this.isModule   = isModule;

    this.hasOnlyImmutableFields = hasOnlyImmutableFields;

    this.superclassAndMixins = superclassAndMixins;
    this.isValueClass        = isValueClass;

    VM.callerNeedsToBeOptimized("instanceLayout should only be accessed on slow path. (and ClassFactory should only be instantiated on slowpath, too)");
    this.instanceLayout = (instanceSlots == null) ? null : new ObjectLayout(instanceSlots, this);
  }

  public ObjectLayout getInstanceLayout() {
    return instanceLayout;
  }

  public void initializeClass(final SClass result) {
    result.initializeClass(className, superclassAndMixins[0]);
    result.initializeStructure(mixinDef, instanceSlots, hasOnlyImmutableFields,
        dispatchables, isValueClass, this);
    initializeClassClass(result);
  }

  private void initializeClassClass(final SClass result) {
    // Initialize the class of the resulting class
    if (result.getSOMClass() != null) {
      SClass classClass = result.getSOMClass();
      classClass.initializeClass(classClassName, Classes.classClass);
      classClass.initializeStructure(mixinDef, null, true, classScope.getDispatchables(), isModule, null);
    }
  }

  public synchronized ObjectLayout updateInstanceLayoutWithInitializedField(
      final SlotDefinition slot, final Class<?> type) {
    ObjectLayout updated = instanceLayout.withInitializedField(slot, type);

    if (updated != instanceLayout) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      instanceLayout = updated;
    }
    return instanceLayout;
  }

  public synchronized ObjectLayout updateInstanceLayoutWithGeneralizedField(
      final SlotDefinition slot) {
    ObjectLayout updated = instanceLayout.withGeneralizedField(slot);

    if (updated != instanceLayout) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      instanceLayout = updated;
    }
    return instanceLayout;
  }
}
