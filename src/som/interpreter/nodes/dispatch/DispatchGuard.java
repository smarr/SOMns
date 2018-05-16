package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.compiler.MixinDefinition;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.vm.SomStructuralType;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;


public abstract class DispatchGuard {
  public abstract boolean entryMatches(Object obj)
      throws InvalidAssumptionException;

  public static DispatchGuard create(final Object obj) {
    if (obj == Boolean.TRUE) {
      return new CheckTrue();
    }

    if (obj == Boolean.FALSE) {
      return new CheckFalse();
    }

    if (obj instanceof SObjectWithoutFields) {
      return new CheckObjectWithoutFields(
          ((SObjectWithoutFields) obj).getFactory());
    }

    if (obj instanceof SClass) {
      return new CheckSClass(((SClass) obj).getFactory());
    }

    if (obj instanceof SMutableObject) {
      return new CheckSMutableObject(((SMutableObject) obj).getObjectLayout());
    }

    if (obj instanceof SImmutableObject) {
      return new CheckSImmutableObject(((SImmutableObject) obj).getObjectLayout());
    }

    return new CheckClass(obj.getClass());
  }

  public static CheckSObject createSObjectCheck(final SObject obj) {
    if (obj instanceof SMutableObject) {
      return new CheckSMutableObject(((SMutableObject) obj).getObjectLayout());
    }

    assert obj instanceof SImmutableObject;
    return new CheckSImmutableObject(((SImmutableObject) obj).getObjectLayout());
  }

  /**
   * Creates an appropriate type-checking dispatch guard. The expected type may be null (used
   * for "unknown" types), and in this case we simply create a no-operation type checking
   * guard. This is done so that every argument always has a corresponding guard, which
   * simplifies the implementation of the loop used to run these guards against their
   * arguments. If the guard is a primitive - a boolean, number, or string - we create a
   * primitive type-checking guard that implements a simple "instance of" check. And finally,
   * if we are given a proper SOM object, we create a full structural type-checking guard.
   */
  public static AbstractTypeCheck createTypeCheck(final SomStructuralType expectedType,
      final Object argument) {
    if (expectedType == null) {
      return new NoTypeCheck();
    } else if (expectedType.isBoolean()) {
      return new BooleanTypeCheck();
    } else if (expectedType.isNumber()) {
      return new NumberTypeCheck();
    } else if (expectedType.isString()) {
      return new StringTypeCheck();
    } else {
      return new StructuralTypeCheck(expectedType, argument);
    }
  }

  public boolean ignore() {
    return false;
  }

  private static final class CheckClass extends DispatchGuard {

    private final Class<?> expected;

    CheckClass(final Class<?> expectedClass) {
      this.expected = expectedClass;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      return obj.getClass() == expected;
    }
  }

  private static final class CheckTrue extends DispatchGuard {
    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      return obj == Boolean.TRUE;
    }
  }

  private static final class CheckFalse extends DispatchGuard {
    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      return obj == Boolean.FALSE;
    }
  }

  private static final class CheckObjectWithoutFields extends DispatchGuard {

    private final ClassFactory expected;

    CheckObjectWithoutFields(final ClassFactory expected) {
      this.expected = expected;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      return obj instanceof SObjectWithoutFields &&
          ((SObjectWithoutFields) obj).getFactory() == expected;
    }
  }

  private static final class CheckSClass extends DispatchGuard {

    private final ClassFactory expected;

    CheckSClass(final ClassFactory expected) {
      this.expected = expected;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      return obj instanceof SClass &&
          ((SClass) obj).getFactory() == expected;
    }
  }

  public abstract static class CheckSObject extends DispatchGuard {
    public abstract SObject cast(Object obj);
  }

  private static final class CheckSMutableObject extends CheckSObject {

    private final ObjectLayout expected;

    CheckSMutableObject(final ObjectLayout expected) {
      this.expected = expected;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      expected.checkIsLatest();
      return obj instanceof SMutableObject &&
          ((SMutableObject) obj).getObjectLayout() == expected;
    }

    @Override
    public SObject cast(final Object obj) {
      return (SMutableObject) obj;
    }
  }

  private static final class CheckSImmutableObject extends CheckSObject {

    private final ObjectLayout expected;

    CheckSImmutableObject(final ObjectLayout expected) {
      this.expected = expected;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      expected.checkIsLatest();
      return obj instanceof SImmutableObject &&
          ((SImmutableObject) obj).getObjectLayout() == expected;
    }

    @Override
    public SObject cast(final Object obj) {
      return (SImmutableObject) obj;
    }
  }

  private static abstract class AbstractTypeCheck extends DispatchGuard {

    protected void throwTypeError(final Object obj, final String expected) {
      throw new IllegalArgumentException(
          "Argument " + obj + " does not conform to the " + expected + " type");
    }

  }

  private static final class NoTypeCheck extends AbstractTypeCheck {
    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      return true;
    }
  }

  private static final class BooleanTypeCheck extends AbstractTypeCheck {

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      if (obj instanceof Boolean) {
        return true;
      } else {
        throwTypeError(obj, "Boolean");
        return false;
      }
    }
  }

  private static final class NumberTypeCheck extends AbstractTypeCheck {

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      if (obj instanceof Number) {
        return true;
      } else {
        throwTypeError(obj, "Number");
        return false;
      }
    }
  }

  private static final class StringTypeCheck extends AbstractTypeCheck {

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      if (obj instanceof String) {
        return true;
      } else {
        throwTypeError(obj, "String");
        return false;
      }
    }

  }

  /**
   * This guard is responsible for determining whether the given argument conforms to the given
   * structural type. Note that the {@link StructuralTypeCheck} is responsible for conducting
   * the type conformity check itself.
   *
   * For efficiency, we perform the structural type check only, during the initialization of
   * this guard. We cache the result after it has been calculated, indexed by the SClass. For
   * the next call we simply examine the given argument; if the class is the same then we
   * assume the result will be the same as was computed previously and simply return true, or
   * otherwise, we invoke the {@link IllegalArgumentException} error.
   *
   * Note that the argument error may be caught by a SOM block and, therefore, we must throw
   * the error during execution rather than initialization.
   *
   */
  private static final class StructuralTypeCheck extends AbstractTypeCheck {

    private final SomStructuralType structuralType;

    @CompilationFinal private final MixinDefinition expected;

    StructuralTypeCheck(final SomStructuralType structuralType, final Object argument) {
      this.structuralType = structuralType;

      if (argument instanceof SObjectWithClass && structuralType.matchesObject(argument)) {
        this.expected = ((SObjectWithClass) argument).getSOMClass().getMixinDefinition();
      } else {
        this.expected = null;
      }
    }

    @Override
    public boolean entryMatches(final Object obj)
        throws InvalidAssumptionException, IllegalArgumentException {

      if (obj instanceof SObjectWithClass
          && ((SObjectWithClass) obj).getSOMClass().getMixinDefinition() == expected) {
        return true;
      } else {
        throwTypeError(obj, structuralType.getName().getString());
        return false;
      }
    }

    @Override
    public boolean ignore() {
      return structuralType == null;
    }
  }
}
