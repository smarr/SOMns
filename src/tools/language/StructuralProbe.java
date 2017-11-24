package tools.language;

import java.util.HashSet;
import java.util.Set;

import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.vmobjects.SInvokable;


public class StructuralProbe {

  protected final Set<MixinDefinition> classes;
  protected final Set<SInvokable>      methods;
  protected final Set<SlotDefinition>  slots;

  public StructuralProbe() {
    classes = new HashSet<>();
    methods = new HashSet<>();
    slots = new HashSet<>();
  }

  public void recordNewClass(final MixinDefinition clazz) {
    classes.add(clazz);
  }

  public void recordNewMethod(final SInvokable method) {
    methods.add(method);
  }

  public void recordNewSlot(final SlotDefinition slot) {
    slots.add(slot);
  }

  public Set<MixinDefinition> getClasses() {
    return classes;
  }

  public Set<SInvokable> getMethods() {
    return methods;
  }

  public Set<SlotDefinition> getSlots() {
    return slots;
  }
}
