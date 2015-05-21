package som.compiler;

import java.util.HashMap;
import java.util.LinkedHashMap;

import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.source.SourceSection;
import com.sun.istack.internal.Nullable;

/**
 * Produced by the Parser, contains all static information on a class that is
 * in the source. Is used to instantiate complete class objects at runtime,
 * which then also have the super class resolved.
 * @author Stefan Marr
 */
public final class ClassDefinition {
  private final SSymbol      name;
  private final SMethod      assembleClassObjectMethod;
  private final HashMap<SSymbol, SInvokable> instanceMethods;
  private final HashMap<SSymbol, SInvokable> factoryMethods;
  private final SourceSection sourceSection;

  @Nullable
  private final LinkedHashMap<SSymbol, ClassDefinition> nestedClassDefinitions;

  @Nullable
  private final LinkedHashMap<SSymbol, SlotDefinition>  slotDefinitions;

  public ClassDefinition(final SSymbol name,
      final SMethod classObjectInstantiation,
      final HashMap<SSymbol, SInvokable> instanceMethods,
      final HashMap<SSymbol, SInvokable> factoryMethods,
      final LinkedHashMap<SSymbol, ClassDefinition> nestedClassDefinitions,
      final LinkedHashMap<SSymbol, SlotDefinition> slotDefinitions,
      final SourceSection sourceSection) {
    this.name = name;
    this.assembleClassObjectMethod = classObjectInstantiation;
    this.instanceMethods = instanceMethods;
    this.factoryMethods  = factoryMethods;
    this.nestedClassDefinitions = nestedClassDefinitions;
    this.slotDefinitions = slotDefinitions;
    this.sourceSection   = sourceSection;
  }

  public SSymbol getName() {
    return name;
  }

  public void initializeClass(final SClass result, final SClass superClass) {
    result.setSuperClass(superClass);

    // build class class name
    String ccName = name.getString() + " class";

    if (result.getSOMClass() != null) {
      // Initialize the class of the resulting class
      result.getSOMClass().setInstanceInvokables(factoryMethods);
      result.getSOMClass().setName(Symbols.symbolFor(ccName));
    }

    // Initialize the resulting class
    result.setName(name);
    result.setInstanceSlots(slotDefinitions);
    result.setInstanceInvokables(instanceMethods);
  }

  public SClass instantiateClass(final SAbstractObject outer, final SClass superClass) {
    SClass resultClass = new SClass(Classes.metaclassClass);
    SClass result = new SClass(resultClass);
    initializeClass(outer, result, superClass);
    return result;
  }

  public static final class SlotDefinition {
    private final SSymbol name;
    private final int index;
    private final AccessModifier modifier;
    private final boolean immutable;
    private final SourceSection source;

    public SlotDefinition(final SSymbol name,
        final AccessModifier acccessModifier, final int index,
        final boolean immutable, final SourceSection source) {
      this.name      = name;
      this.modifier  = acccessModifier;
      this.index     = index;
      this.immutable = immutable;
      this.source    = source;
    }

    public SSymbol getName() {
      return name;
    }

    public AccessModifier getModifier() {
      return modifier;
    }

    public boolean isImmutable() {
      return immutable;
    }

    @Override
    public String toString() {
      String imm = immutable ? ", immut" : "";
      return "SlotDef(" + name.getString()
          + " :" + modifier.toString().toLowerCase() + imm + ")";
    }
  }

  public ClassDefinition getEmbeddedClassDefinition(final String string) {
    if (nestedClassDefinitions == null) {
      return null;
    }
    return nestedClassDefinitions.get(Symbols.symbolFor(string));
  }

  public int getNumberOfSlots() {
    if (slotDefinitions == null) {
      return 0;
    }
    return slotDefinitions.size();
  }

  public SourceSection getSourceSection() {
    return sourceSection;
  }
}
