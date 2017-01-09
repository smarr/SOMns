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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.ClassSlotDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.vm.ObjectSystem;
import som.vm.constants.Classes;


// TODO: should we move more of that out of SClass and use the corresponding
//       ClassFactory?
public final class SClass extends SObjectWithClass {

  @CompilationFinal private SClass superclass;
  @CompilationFinal private SSymbol name;

  @CompilationFinal private HashMap<SSymbol, Dispatchable> dispatchables;
  @CompilationFinal private HashSet<SlotDefinition> slots; // includes slots of super classes and mixins

  @CompilationFinal private MixinDefinition mixinDef;
  @CompilationFinal private boolean declaredAsValue;
  @CompilationFinal private boolean isTransferObject; // is a kind of TransferObject (subclass or TObj directly)
  @CompilationFinal private boolean isArray; // is a subclass of Array

  @CompilationFinal private ClassFactory instanceClassGroup; // the factory for this object

  protected final SObjectWithClass enclosingObject;

  public SClass(final SObjectWithClass enclosing) {
    this.enclosingObject = enclosing;
  }

  public SClass(final SObjectWithClass enclosing, final SClass clazz) {
    super(clazz, clazz.getInstanceFactory());
    this.enclosingObject = enclosing;
  }

  public SObjectWithClass getEnclosingObject() {
    return enclosingObject;
  }

  public SClass getSuperClass() {
    return superclass;
  }

  public ClassFactory getInstanceFactory() {
    assert classGroup != null               || !ObjectSystem.isInitialized();
    assert instanceClassGroup != null       || !ObjectSystem.isInitialized();
    assert classGroup != instanceClassGroup || !ObjectSystem.isInitialized();
    return instanceClassGroup;
  }

  public ObjectLayout getLayoutForInstances() {
    return instanceClassGroup.getInstanceLayout();
  }

  public HashSet<SlotDefinition> getInstanceSlots() {
    return slots;
  }

  public void initializeClass(final SSymbol name, final SClass superclass) {
    assert (this.name == null || this.name == name) && (this.superclass == null || this.superclass == superclass) : "Should only be initialized once";
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
      final HashSet<SlotDefinition> slots,
      final HashMap<SSymbol, Dispatchable> dispatchables,
      final boolean declaredAsValue, final boolean isTransferObject,
      final boolean isArray,
      final ClassFactory classFactory) {
    assert slots == null || slots.size() > 0;

    this.mixinDef  = mixinDef;
    this.slots     = slots;
    this.dispatchables   = dispatchables;
    this.declaredAsValue = declaredAsValue;
    this.isTransferObject = isTransferObject;
    this.isArray          = isArray;
    this.instanceClassGroup = classFactory;
    assert instanceClassGroup != null || !ObjectSystem.isInitialized();
  }

  private boolean isBasedOn(final MixinDefinitionId mixinId) {
    return this.mixinDef.getMixinId() == mixinId;
  }

  /**
   * Checks whether the classes are the same, including superclass hierarchy
   * and ignoring class identity, i.e., relying on class groups/factories, too.
   */
  public boolean isKindOf(final SClass clazz) {
    if (this == clazz) { return true; }
    if (this == Classes.topClass) { return false; }
    if (this.instanceClassGroup == clazz.instanceClassGroup) { return true; }
    return superclass.isKindOf(clazz);
  }

  public SClass getClassCorrespondingTo(final MixinDefinitionId mixinId) {
    VM.callerNeedsToBeOptimized("This should not be on the fast path, specialization/caching needed?");
    SClass cls = this;
    while (cls != null && !cls.isBasedOn(mixinId)) {
      cls = cls.getSuperClass();
    }
    return cls;
  }

  @ExplodeLoop
  public SClass getClassCorrespondingTo(final int superclassIdx) {
    SClass cls = this;
    for (int i = 0; i < superclassIdx && cls != null; i++) {
      cls = cls.getSuperClass();
    }
    return cls;
  }

  public int getIdxForClassCorrespondingTo(final MixinDefinitionId mixinId) {
    VM.callerNeedsToBeOptimized("This should not be on the fast path, specialization/caching needed?");
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

  public SInvokable[] getMethods() {
    ArrayList<SInvokable> methods = new ArrayList<SInvokable>();
    for (Dispatchable disp : dispatchables.values()) {
      if (disp instanceof SInvokable) {
        methods.add((SInvokable) disp);
      }
    }
    return methods.toArray(new SInvokable[methods.size()]);
  }

  public SClass[] getNestedClasses(final SObjectWithClass instance) {
    VM.thisMethodNeedsToBeOptimized("Not optimized, we do unrecorded invokes here");
    ArrayList<SClass> classes = new ArrayList<SClass>();
    for (Dispatchable disp : dispatchables.values()) {
      if (disp instanceof ClassSlotDefinition) {
        classes.add((SClass) disp.invoke(null, null, instance));
      }
    }
    return classes.toArray(new SClass[classes.size()]);
  }

  public Map<SSymbol, Dispatchable> getDispatchables() {
    return dispatchables;
  }

  public Dispatchable lookupPrivate(final SSymbol selector,
      final MixinDefinitionId mixinId) {
    VM.callerNeedsToBeOptimized("should never be called on fast path");

    SClass cls = getClassCorrespondingTo(mixinId);
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
   *                   is allowed to have
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
}
