package tools.language;

import java.util.HashSet;
import java.util.Set;

import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.Variable;
import som.vmobjects.SInvokable;


public class StructuralProbe {

  protected final Set<MixinDefinition> classes;
  protected final Set<SInvokable>      methods;
  protected final Set<SlotDefinition>  slots;
  protected final Set<Variable>        variables;

  public StructuralProbe() {
    classes = new HashSet<>();
    methods = new HashSet<>();
    slots = new HashSet<>();
    variables = new HashSet<>();
  }

  public synchronized void recordNewClass(final MixinDefinition clazz) {
    classes.add(clazz);
  }

  public synchronized void recordNewMethod(final SInvokable method) {
    methods.add(method);
  }

  public synchronized void recordNewSlot(final SlotDefinition slot) {
    slots.add(slot);
  }

  public synchronized void recordNewVariable(final Variable var) {
    variables.add(var);
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

  public Set<Variable> getVariables() {
    return variables;
  }
}
