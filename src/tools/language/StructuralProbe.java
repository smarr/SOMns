package tools.language;

import java.util.HashSet;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

import som.compiler.MixinDefinition;
import som.compiler.MixinDefinition.SlotDefinition;
import som.compiler.Variable;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


public class StructuralProbe {

  protected final Set<MixinDefinition>             classes;
  protected final EconomicMap<SSymbol, SInvokable> methods;
  protected final Set<SlotDefinition>              slots;
  protected final Set<Variable>                    variables;

  public StructuralProbe() {
    classes = new HashSet<>();
    methods = EconomicMap.create();
    slots = new HashSet<>();
    variables = new HashSet<>();
  }

  public synchronized void recordNewClass(final MixinDefinition clazz) {
    classes.add(clazz);
  }

  public synchronized void recordNewMethod(final SInvokable method) {
    // make sure we don't lose an invokable
    assert methods.get(method.getIdentifier(), method) == method;
    methods.put(method.getIdentifier(), method);
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
    HashSet<SInvokable> res = new HashSet<>();
    for (SInvokable si : methods.getValues()) {
      res.add(si);
    }
    return res;
  }

  public Set<SlotDefinition> getSlots() {
    return slots;
  }

  public Set<Variable> getVariables() {
    return variables;
  }

  public SInvokable lookupMethod(final SSymbol sym) {
    return methods.get(sym);
  }
}
