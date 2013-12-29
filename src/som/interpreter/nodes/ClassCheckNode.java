package som.interpreter.nodes;

import java.math.BigInteger;

import som.vm.Universe;
import som.vmobjects.SArray;
import som.vmobjects.SBigInteger;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SDouble;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.nodes.Node;

public abstract class ClassCheckNode extends Node {

  public abstract boolean execute(final Object obj);

  @Override
  public String toString() {
    return getClass().getName();
  }

  public static final class Uninitialized extends ClassCheckNode {
    private final boolean  isSuperSend;
    private final SClass   cachedClass;
    private final Universe universe;

    public Uninitialized(final SClass cachedClass, final boolean isSuperSend,
        final Universe universe) {
      this.cachedClass = cachedClass;
      this.isSuperSend = isSuperSend;
      this.universe    = universe;
    }

    @Override
    public String toString() {
      return super.toString() + "(" + cachedClass.getName().getString()
          + (isSuperSend ? ", super send" : "")
          + ")";
    }

    @Override
    public boolean execute(final Object obj) {
      return specialize().execute(obj);
    }

    private ClassCheckNode specialize() {
      if (isSuperSend) {
        return replace(new SuperSendCheckNode());
      } else if (cachedClass == universe.integerClass) {
        return replace(new SIntegerCheckNode());
      } else if (cachedClass == universe.bigintegerClass) {
        return replace(new SBigIntegerCheckNode());
      } else if (cachedClass == universe.arrayClass) {
        return replace(new SArrayCheckNode());
      } else if (cachedClass == universe.doubleClass) {
        return replace(new SDoubleCheckNode());
      } else if (cachedClass == universe.methodClass) {
        return replace(new SMethodCheckNode(false));
      } else if (cachedClass == universe.primitiveClass) {
        return replace(new SMethodCheckNode(true));
      } else if (cachedClass == universe.stringClass) {
        return replace(new SStringCheckNode());
      } else if (cachedClass == universe.symbolClass) {
        return replace(new SSymbolCheckNode());
      } else if (universe.isBlockClass(cachedClass)) {
        return replace(new SBlockCheckNode(cachedClass, universe));
      } else {
        return replace(new SObjectCheckNode(cachedClass, universe));
      }
    }
  }

  /**
   * Super sends are special, they lead to a lexically defined receiver class.
   * So, it's always the cached receiver. TODO: should be true, at least that's
   * TruffleSOM's implementation. I still feel a little uneasy... Hope, I don't missing anything.
   */
  public static final class SuperSendCheckNode extends ClassCheckNode {
    @Override public boolean execute(final Object obj) { return true; }
  }

  public static final class SIntegerCheckNode extends ClassCheckNode {
    @Override
    public boolean execute(final Object obj) {
      return obj instanceof Integer || obj instanceof SInteger;
    }
  }

  public static final class SBigIntegerCheckNode extends ClassCheckNode {
    @Override
    public boolean execute(final Object obj) {
      return obj instanceof BigInteger || obj instanceof SBigInteger;
    }
  }

  public static final class SArrayCheckNode extends ClassCheckNode {
    @Override
    public boolean execute(final Object obj) {
      return obj instanceof SArray;
    }
  }

  public static final class SDoubleCheckNode extends ClassCheckNode {
    @Override
    public boolean execute(final Object obj) {
      return obj instanceof Double || obj instanceof SDouble;
    }
  }

  public static final class SStringCheckNode extends ClassCheckNode {
    @Override
    public boolean execute(final Object obj) {
      return obj instanceof String || obj instanceof SString;
    }
  }

  public static final class SSymbolCheckNode extends ClassCheckNode {
    @Override
    public boolean execute(final Object obj) {
      return obj instanceof SSymbol;
    }
  }

  public static final class SMethodCheckNode extends ClassCheckNode {
    private final boolean isPrimitive;

    public SMethodCheckNode(final boolean isPrimitive) {
      this.isPrimitive = isPrimitive;
    }

    @Override
    public boolean execute(final Object obj) {
      return obj instanceof SMethod
          && ((SMethod) obj).isPrimitive() == isPrimitive;
    }

    @Override
    public String toString() {
      return super.toString() + "(" + (isPrimitive ? "Primitive)" : "Method)");
    }
  }


  public static class SObjectCheckNode extends ClassCheckNode {
    protected final SClass cachedClass;
    protected final Universe universe;

    public SObjectCheckNode(final SClass cachedClass, final Universe universe) {
      this.cachedClass = cachedClass;
      this.universe    = universe;
    }

    @Override
    public boolean execute(final Object obj) {
      if (obj instanceof SObject) {
        return ((SObject) obj).getSOMClass(universe) == cachedClass;
      } else {
        return false;
      }
    }

    @Override
    public String toString() {
      return super.toString() + "(" + cachedClass.getName().getString() + ")";
    }
  }

  public static final class SBlockCheckNode extends SObjectCheckNode {
    public SBlockCheckNode(final SClass cachedClass, final Universe universe) {
      super(cachedClass, universe);
    }

    @Override
    public boolean execute(final Object obj) {
      if (obj instanceof SBlock) {
        return ((SBlock) obj).getSOMClass(universe) == cachedClass;
      } else {
        return false;
      }
    }
  }
}
