package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.MixinDefinition;
import som.interpreter.Types;
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
  public abstract boolean entryMatches(Object obj, SourceSection sourceSection)
      throws InvalidAssumptionException, IllegalArgumentException;

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
  public static AbstractTypeCheck createTypeCheck(final SomStructuralType expectedType) {
    if (expectedType == null) {
      return new NoTypeCheck();
    } else {
      return new StructuralTypeCheck(expectedType);
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
    public boolean entryMatches(final Object obj, final SourceSection sourceSection)
        throws InvalidAssumptionException {
      return obj.getClass() == expected;
    }
  }

  private static final class CheckTrue extends DispatchGuard {
    @Override
    public boolean entryMatches(final Object obj, final SourceSection sourceSection)
        throws InvalidAssumptionException {
      return obj == Boolean.TRUE;
    }
  }

  private static final class CheckFalse extends DispatchGuard {
    @Override
    public boolean entryMatches(final Object obj, final SourceSection sourceSection)
        throws InvalidAssumptionException {
      return obj == Boolean.FALSE;
    }
  }

  private static final class CheckObjectWithoutFields extends DispatchGuard {

    private final ClassFactory expected;

    CheckObjectWithoutFields(final ClassFactory expected) {
      this.expected = expected;
    }

    @Override
    public boolean entryMatches(final Object obj, final SourceSection sourceSection)
        throws InvalidAssumptionException {
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
    public boolean entryMatches(final Object obj, final SourceSection sourceSection)
        throws InvalidAssumptionException {
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
    public boolean entryMatches(final Object obj, final SourceSection sourceSection)
        throws InvalidAssumptionException {
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
    public boolean entryMatches(final Object obj, final SourceSection sourceSection)
        throws InvalidAssumptionException {
      expected.checkIsLatest();
      return obj instanceof SImmutableObject &&
          ((SImmutableObject) obj).getObjectLayout() == expected;
    }

    @Override
    public SObject cast(final Object obj) {
      return (SImmutableObject) obj;
    }
  }

  public static abstract class AbstractTypeCheck extends DispatchGuard {

    protected void throwTypeError(final Object obj, final SomStructuralType expected,
        final SourceSection sourceSection) throws IllegalArgumentException {
      CompilerDirectives.transferToInterpreter();
      String prefix = "";
      if (sourceSection != null) {
        prefix =
            "[" + sourceSection.getStartLine() + "," + sourceSection.getStartColumn() + "]";
      }

      SomStructuralType objType;
      if (obj instanceof SObjectWithClass) {
        objType = SomStructuralType.getTypeFromMixin(
            ((SObjectWithClass) obj).getSOMClass().getMixinDefinition());
      } else {
        objType =
            SomStructuralType.getTypeFromMixin(Types.getClassOf(obj).getMixinDefinition());
      }
      throw new IllegalArgumentException(
          prefix + " " + objType + " does not implemenet the interface " + expected);
    }

  }

  public static final class NoTypeCheck extends AbstractTypeCheck {
    @Override
    public boolean entryMatches(final Object obj, final SourceSection sourceSection)
        throws InvalidAssumptionException {
      return true;
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
  public static final class StructuralTypeCheck extends AbstractTypeCheck {

    private final SomStructuralType   expected;
    private final MixinDefinitionId[] cacheIds;
    private final boolean[]           cacheResults;
    private final int                 cacheSize;
    private int                       nCached = 0;

    StructuralTypeCheck(final SomStructuralType structuralType, final int cacheSize) {
      this.expected = structuralType;
      this.cacheSize = cacheSize;
      this.cacheIds = new MixinDefinitionId[cacheSize];
      this.cacheResults = new boolean[cacheSize];
    }

    public StructuralTypeCheck(final SomStructuralType structuralType) {
      this(structuralType, 10);
    }

    public boolean doTypeCheckOnSlowPath(final MixinDefinition mixinDef) {
      CompilerDirectives.transferToInterpreter();
      SomStructuralType objType = mixinDef.getType();
      boolean ret = (objType.isSubclassOf(expected));
      cacheIds[nCached] = mixinDef.getMixinId();
      cacheResults[nCached] = ret;
      nCached += 1;
      return ret;
    }

    @Override
    public boolean entryMatches(final Object obj, final SourceSection sourceSection)
        throws InvalidAssumptionException, IllegalArgumentException {

      MixinDefinition mixinDef;
      if (obj instanceof SObjectWithClass) {
        mixinDef = ((SObjectWithClass) obj).getSOMClass().getMixinDefinition();
      } else {
        mixinDef = Types.getClassOf(obj).getMixinDefinition();
      }

      MixinDefinitionId id = mixinDef.getMixinId();
      for (int i = 0; i < cacheSize; i++) {
        if (id == cacheIds[i]) {
          if (cacheResults[i]) {
            return true;
          } else {
            throwTypeError(obj, expected, sourceSection);
            return false;
          }
        }
      }

      if (doTypeCheckOnSlowPath(mixinDef)) {
        return true;
      } else {
        throwTypeError(obj, expected, sourceSection);
        return false;
      }
    }

    @Override
    public boolean ignore() {
      return expected == null;
    }
  }
}
