package som.vmobjects;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import som.interpreter.nodes.dispatch.TypeCheckNode;
import som.vm.VmSettings;


public abstract class SType extends SObjectWithClass {
  @CompilationFinal public static SClass typeClass;
  public static List<SType>              missingClass = new LinkedList<>();
  private static int                     count        = 0;

  public final int id;

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
    id = count++;
  }

  @Override
  public boolean isValue() {
    return true;
  }

  public abstract SSymbol[] getSignatures();

  public abstract boolean isSuperTypeOf(final SType other, final Object inst);

  public static class InterfaceType extends SType {
    @CompilationFinal(dimensions = 1) public final SSymbol[] signatures;

    public InterfaceType(final SSymbol[] signatures) {
      this.signatures = signatures;
    }

    @Override
    public boolean isSuperTypeOf(final SType other, final Object inst) {
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
    public boolean isSuperTypeOf(final SType other, final Object inst) {
      return left.isSuperTypeOf(other, inst) && right.isSuperTypeOf(other, inst);
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
    public boolean isSuperTypeOf(final SType other, final Object inst) {
      return left.isSuperTypeOf(other, inst) || right.isSuperTypeOf(other, inst);
    }

    @Override
    public SSymbol[] getSignatures() {
      // Signatures from variant types are not usable
      return new SSymbol[] {};
    }
  }

  public static class BrandType extends SType {

    private final Set<Object> brandedObjects = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public boolean isSuperTypeOf(final SType other, final Object inst) {
      return brandedObjects.contains(inst);
    }

    @Override
    public SSymbol[] getSignatures() {
      // Signatures from brand types are not usable
      return new SSymbol[] {};
    }

    public void brand(final Object o) {
      brandedObjects.add(o);
    }
  }
}
