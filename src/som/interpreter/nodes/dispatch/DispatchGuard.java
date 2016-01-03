package som.interpreter.nodes.dispatch;

import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;

import com.oracle.truffle.api.nodes.InvalidAssumptionException;


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

  private static final class CheckClass extends DispatchGuard {

    private final Class<?> expected;

    public CheckClass(final Class<?> expectedClass) {
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

    public CheckObjectWithoutFields(final ClassFactory expected) {
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

    public CheckSClass(final ClassFactory expected) {
      this.expected = expected;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      return obj instanceof SClass &&
          ((SClass) obj).getFactory() == expected;
    }
  }

  private static final class CheckSMutableObject extends DispatchGuard {

    private final ObjectLayout expected;

    public CheckSMutableObject(final ObjectLayout expected) {
      this.expected = expected;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      expected.checkIsLatest();
      return obj instanceof SMutableObject &&
          ((SMutableObject) obj).getObjectLayout() == expected;
    }
  }

  private static final class CheckSImmutableObject extends DispatchGuard {

    private final ObjectLayout expected;

    public CheckSImmutableObject(final ObjectLayout expected) {
      this.expected = expected;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      expected.checkIsLatest();
      return obj instanceof SImmutableObject &&
          ((SImmutableObject) obj).getObjectLayout() == expected;
    }
  }
}
