package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.compiler.MixinDefinition.SlotDefinition;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.interpreter.objectstorage.StorageLocation;
import som.vmobjects.SClass;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;


public abstract class DispatchGuard {

  @CompilerDirectives.CompilationFinal
  private static Assumption noModuleReloaded = Truffle.getRuntime().createAssumption("no module reloaded");

  public static void invalidateAssumption() {
    noModuleReloaded.invalidate();
    noModuleReloaded = Truffle.getRuntime().createAssumption("noMoRel");
  }

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

  private static final class CheckClass extends DispatchGuard {

    private final Class<?> expected;
    private final Assumption noLoad = noModuleReloaded;

    CheckClass(final Class<?> expectedClass) {
      this.expected = expectedClass;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      noLoad.check();
      return obj.getClass() == expected;
    }
  }

  private static final class CheckTrue extends DispatchGuard {

    private final Assumption noLoad = noModuleReloaded;

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      noLoad.check();
      return obj == Boolean.TRUE;
    }
  }

  private static final class CheckFalse extends DispatchGuard {

    private final Assumption noLoad = noModuleReloaded;

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      noLoad.check();
      return obj == Boolean.FALSE;
    }
  }

  private static final class CheckObjectWithoutFields extends DispatchGuard {

    private final ClassFactory expected;
    private final Assumption noLoad = noModuleReloaded;

    CheckObjectWithoutFields(final ClassFactory expected) {
      this.expected = expected;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      noLoad.check();
      return obj instanceof SObjectWithoutFields &&
          ((SObjectWithoutFields) obj).getFactory() == expected;
    }
  }

  private static final class CheckSClass extends DispatchGuard {

    private final ClassFactory expected;
    private final Assumption noLoad = noModuleReloaded;

    CheckSClass(final ClassFactory expected) {
      this.expected = expected;
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      noLoad.check();
      return obj instanceof SClass &&
          ((SClass) obj).getFactory() == expected;
    }
  }

  public abstract static class CheckSObject extends DispatchGuard {
    protected final ObjectLayout expected;
    protected final Assumption noLoad = noModuleReloaded;

    CheckSObject(final ObjectLayout expected) {
      this.expected = expected;
    }

    public abstract SObject cast(Object obj);

    public final boolean isObjectSlotAllocated(final SlotDefinition slotDef) {
      StorageLocation loc = expected.getStorageLocation(slotDef);
      return loc.isObjectLocation();
    }
  }

  private static final class CheckSMutableObject extends CheckSObject {

    CheckSMutableObject(final ObjectLayout expected) {
      super(expected);
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      noLoad.check();
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

    CheckSImmutableObject(final ObjectLayout expected) {
      super(expected);
    }

    @Override
    public boolean entryMatches(final Object obj) throws InvalidAssumptionException {
      noLoad.check();
      expected.checkIsLatest();
      return obj instanceof SImmutableObject &&
          ((SImmutableObject) obj).getObjectLayout() == expected;
    }

    @Override
    public SObject cast(final Object obj) {
      return (SImmutableObject) obj;
    }
  }
}
