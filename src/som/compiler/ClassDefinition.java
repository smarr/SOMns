package som.compiler;

import som.vmobjects.SSymbol;

/**
 * Produced by the Parser, contains all static information on a class that is
 * in the source. Is used to instantiate complete class objects at runtime,
 * which then also have the super class resolved.
 * @author Stefan Marr
 */
public final class ClassDefinition {
  private final SSymbol name;

  public ClassDefinition(final SSymbol name) {
    this.name = name;
  }

  public SSymbol getName() {
    return name;
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
