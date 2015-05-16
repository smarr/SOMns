package som.compiler;

import som.vm.NotYetImplementedException;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable.SMethod;
import som.vmobjects.SSymbol;

/**
 * Produced by the Parser, contains all static information on a class that is
 * in the source. Is used to instantiate complete class objects at runtime,
 * which then also have the super class resolved.
 * @author Stefan Marr
 */
public final class ClassDefinition {
  private final SSymbol name;
  private final SMethod   assembleClassObjectMethod;
  private final SMethod[] instanceMethods;
  private final SMethod[] factoryMethods;

  public ClassDefinition(final SSymbol name,
      final SMethod classObjectInstantiation, final SMethod[] instanceMethods,
      final SMethod[] factoryMethods) {
    this.name = name;
    this.assembleClassObjectMethod = classObjectInstantiation;
    this.instanceMethods = instanceMethods;
    this.factoryMethods  = factoryMethods;
  }

  public SSymbol getName() {
    return name;
  }

  public SClass createSClass(final SClass superClass) {
    // build class class name
    String ccName = name.getString() + " class";

//  // Allocate the class of the resulting class
//  SClass resultClass = Universe.newClass(Classes.metaclassClass);
    //
    //
//        // Initialize the class of the resulting class
//        resultClass.setInstanceInvokables(
//            SArray.create(factoryMethods.toArray(new Object[0])));
//        resultClass.setName(Symbols.symbolFor(ccname));
    //
//        SClass superMClass = superClass.getSOMClass();
//        resultClass.setSuperClass(superMClass);
    //
//        // Allocate the resulting class
//        SClass result = Universe.newClass(resultClass);
    //
//        // Initialize the resulting class
//        result.setName(name);
//        result.setSuperClass(superClass);
//        result.setInstanceFields(
//            SArray.create(slots.toArray(new Object[0])));
//        result.setInstanceInvokables(
//            SArray.create(methods.toArray(new Object[0])));
    //
//        return result;
    throw new NotYetImplementedException();
  }

  public static final class SlotDefinition {
    private final SSymbol name;
    private final AccessModifier modifier;
    private final boolean immutable;

    public SlotDefinition(final SSymbol name,
        final AccessModifier acccessModifier, final boolean immutable) {
      this.name      = name;
      this.modifier  = acccessModifier;
      this.immutable = immutable;
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
}
