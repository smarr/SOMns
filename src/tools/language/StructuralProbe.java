package tools.language;

import java.util.HashSet;
import java.util.Set;

import som.compiler.MixinDefinition;
import som.vmobjects.SInvokable;


public class StructuralProbe {

  // TODO: make language independent, for instance by using fully qualified
  //       names
  private final Set<MixinDefinition> classes;
  private final Set<SInvokable>      methods;

  public StructuralProbe() {
    classes = new HashSet<>();
    methods = new HashSet<>();
  }

  public void recordNewClass(final MixinDefinition clazz) {
    classes.add(clazz);
  }

  public void recordNewMethod(final SInvokable method) {
    methods.add(method);
  }

  public Set<MixinDefinition> getClasses() {
    return classes;
  }

  public Set<SInvokable> getMethods() {
    return methods;
  }
}
