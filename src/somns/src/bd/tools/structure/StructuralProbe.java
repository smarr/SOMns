package bd.tools.structure;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;


/**
 * A {@code StructuralProbe} records information about the basic structural elements of a
 * program.
 *
 * <p>
 * Currently, the main elements recorded are classes, methods, slots, and variables.
 * The interpretation of the concrete semantic is fairly liberal, and may vary from tool to
 * tool using the data.
 *
 * <p>
 * <strong>Synchronization:</strong> There is an assumption that these probes may be accessed
 * by multiple threads. At the moment, we only do minimal synchronization.
 * This may need to become more sophisticated in case it turns out that performance is an
 * issue.
 *
 * @param <Id> the type of the identifiers used for the various elements, typically some form
 *          of interned string construct
 * @param <Klass> the type used to represent class-like abstractions
 * @param <Method> the type used to represent method or function-like abstractions
 * @param <Slot> the type used to represent field or slot like abstractions
 * @param <Variable> the type used to represent variable, parameters, and arguments
 */
public class StructuralProbe<Id, Klass, Method, Slot, Variable> {

  protected final EconomicSet<Klass>      classes;
  protected final EconomicMap<Id, Method> methods;
  protected final EconomicSet<Slot>       slots;
  protected final EconomicSet<Variable>   variables;

  public StructuralProbe() {
    classes = EconomicSet.create();
    methods = EconomicMap.create();
    slots = EconomicSet.create();
    variables = EconomicSet.create();
  }

  public synchronized void recordNewClass(final Klass clazz) {
    classes.add(clazz);
  }

  public synchronized void recordNewMethod(final Id id, final Method method) {
    // make sure we don't lose an invokable
    assert methods.get(id,
        method) == method : "It is not expected that a method with the same id is present, "
            + "but we found: " + id;
    methods.put(id, method);
  }

  public synchronized void recordNewSlot(final Slot slot) {
    slots.add(slot);
  }

  public synchronized void recordNewVariable(final Variable var) {
    variables.add(var);
  }

  public EconomicSet<Klass> getClasses() {
    return classes;
  }

  public EconomicSet<Method> getMethods() {
    EconomicSet<Method> res = EconomicSet.create();
    for (Method si : methods.getValues()) {
      res.add(si);
    }
    return res;
  }

  public EconomicSet<Slot> getSlots() {
    return slots;
  }

  public EconomicSet<Variable> getVariables() {
    return variables;
  }

  public Method lookupMethod(final Id id) {
    return methods.get(id);
  }
}
