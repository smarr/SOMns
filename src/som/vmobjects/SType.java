package som.vmobjects;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import som.interpreter.nodes.dispatch.TypeCheckNode;
import som.vm.VmSettings;


public abstract class SType extends SObjectWithClass {
  @CompilationFinal public static SClass typeClass;
  public static List<SType>              missingClass = new LinkedList<>();

  public static void setSOMClass(final SClass cls) {
    typeClass = cls;
    for (SType typeWithoutClass : missingClass) {
      typeWithoutClass.setClass(typeClass);
    }
    missingClass = null;
  }

  public SType() {
    super();
    if (typeClass == null) {
      missingClass.add(this);
    } else {
      this.setClass(typeClass);
    }
    if (VmSettings.COLLECT_TYPE_STATS) {
      ++TypeCheckNode.nTypes;
    }
  }

  @Override
  public boolean isValue() {
    return true;
  }

  public abstract SSymbol[] getSignatures();

  public abstract boolean isSuperTypeOf(final SType other);

  public static class InterfaceType extends SType {
    @CompilationFinal(dimensions = 1) public final SSymbol[] signatures;

    public InterfaceType(final SSymbol[] signatures) {
      this.signatures = signatures;
    }

    @Override
    public boolean isSuperTypeOf(final SType other) {
      for (SSymbol sigThis : signatures) {
        boolean found = false;
        for (SSymbol sigOther : other.getSignatures()) {
          if (sigThis == sigOther) {
            found = true;
            break;
          }
        }
        if (!found) {
          return false;
        }
      }
      return true;
    }

    @Override
    public SSymbol[] getSignatures() {
      return signatures;
    }

    @Override
    public String toString() {
      String s = "interface {";
      boolean first = true;
      for (SSymbol sig : signatures) {
        if (sig.getString().startsWith("!!!")) {
          continue;
        }
        if (first) {
          s += "'" + sig.getString() + "'";
          first = false;
        } else {
          s += ", '" + sig.getString() + "'";
        }
      }
      return s + "}";
    }
  }

  // SELF TYPE???

  public static class IntersectionType extends SType {

    public final SType left;
    public final SType right;

    public IntersectionType(final SType left, final SType right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public boolean isSuperTypeOf(final SType other) {
      return left.isSuperTypeOf(other) && right.isSuperTypeOf(other);
    }

    @Override
    public SSymbol[] getSignatures() {
      Set<SSymbol> set = new HashSet<>();
      set.addAll(Arrays.asList(left.getSignatures()));
      set.addAll(Arrays.asList(right.getSignatures()));
      return set.toArray(new SSymbol[set.size()]);
    }
  }

  public static class VariantType extends SType {

    public final SType left;
    public final SType right;

    public VariantType(final SType left, final SType right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public boolean isSuperTypeOf(final SType other) {
      return left.isSuperTypeOf(other) || right.isSuperTypeOf(other);
    }

    @Override
    public SSymbol[] getSignatures() {
      // Signatures from variant types are not usable
      return new SSymbol[] {};
    }
  }
}
