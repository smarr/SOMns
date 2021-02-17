package som.interpreter.objectstorage;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.IntValueProfile;

import som.compiler.MixinDefinition.SlotDefinition;
import som.vmobjects.SObject;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import sun.misc.Unsafe;
import tools.UnsafeUtil;


/**
 * StorageAccessors represent the concrete memory locations in the object
 * storage and provide access to them. Since the underlying storage is fixed,
 * there is only a fixed predetermined number of accessors at run time.
 * Furthermore, they are independent from the layout. {@link StorageLocation}
 * object create the connection between the {@link ObjectLayout} and a
 * {@link SlotDefinition slot's} mapping to a specific memory location.
 * The available memory locations are defined in {@link SImmutableObject} and
 * {@link SMutableObject}.
 */
public abstract class StorageAccessor {
  private static final Unsafe unsafe = UnsafeUtil.load();

  private static final int MAX_OBJECT_FIELDS = 50;
  private static final int MAX_PRIM_FIELDS   = 30;

  private static final long SMO_PRIM_FIELD_1_OFFSET = getFieldOffset("primField1");
  private static final long SMO_PRIM_FIELD_2_OFFSET = getFieldOffset("primField2");
  private static final long SMO_PRIM_FIELD_3_OFFSET = getFieldOffset("primField3");
  private static final long SMO_PRIM_FIELD_4_OFFSET = getFieldOffset("primField4");
  private static final long SMO_PRIM_FIELD_5_OFFSET = getFieldOffset("primField5");
  private static final long SMO_FIELD_1_OFFSET      = getFieldOffset("field1");
  private static final long SMO_FIELD_2_OFFSET      = getFieldOffset("field2");
  private static final long SMO_FIELD_3_OFFSET      = getFieldOffset("field3");
  private static final long SMO_FIELD_4_OFFSET      = getFieldOffset("field4");
  private static final long SMO_FIELD_5_OFFSET      = getFieldOffset("field5");

  @CompilationFinal(
      dimensions = 1) private static final AbstractObjectAccessor[]                  objAccessors  =
          new AbstractObjectAccessor[MAX_OBJECT_FIELDS];
  @CompilationFinal(
      dimensions = 1) private static final AbstractPrimitiveAccessor[]               primAccessors =
          new AbstractPrimitiveAccessor[MAX_PRIM_FIELDS];

  private static long getFieldOffset(final String fieldName) {
    try {
      Field field = SMutableObject.class.getDeclaredField(fieldName);
      return unsafe.objectFieldOffset(field);
    } catch (NoSuchFieldException | SecurityException e) {
      throw new RuntimeException(e);
    }
  }

  public static AbstractObjectAccessor getObjectAccessor(final int idx) {
    assert idx < MAX_OBJECT_FIELDS : "Got a object slot allocated that goes beyond the currently supported. idx: "
        + idx;
    AbstractObjectAccessor result = objAccessors[idx];
    assert result != null : "Object accessors not yet initialized?";
    return result;
  }

  public static AbstractPrimitiveAccessor getPrimitiveAccessor(final int idx) {
    assert idx < MAX_OBJECT_FIELDS : "Got a primitive slot allocated that goes beyond the currently supported. idx: "
        + idx;
    AbstractPrimitiveAccessor result = primAccessors[idx];
    assert result != null : "Primitive accessors not yet initialized?";
    return result;
  }

  /**
   * Initialize field accessors with the offsets in the S*Object classes.
   */
  @TruffleBoundary
  public static void initAccessors() {
    initObjectAccessors();
    initPrimitiveAccessors();
  }

  private static void initObjectAccessors() {
    objAccessors[0] = new DirectObjectAccessor(SMO_FIELD_1_OFFSET);
    objAccessors[1] = new DirectObjectAccessor(SMO_FIELD_2_OFFSET);
    objAccessors[2] = new DirectObjectAccessor(SMO_FIELD_3_OFFSET);
    objAccessors[3] = new DirectObjectAccessor(SMO_FIELD_4_OFFSET);
    objAccessors[4] = new DirectObjectAccessor(SMO_FIELD_5_OFFSET);
    for (int i = SObject.NUM_OBJECT_FIELDS; i < MAX_OBJECT_FIELDS; i += 1) {
      objAccessors[i] = new ExtensionObjectAccessor(i);
    }
  }

  private static void initPrimitiveAccessors() {
    primAccessors[0] = new DirectPrimitiveAccessor(SMO_PRIM_FIELD_1_OFFSET, 0);
    primAccessors[1] = new DirectPrimitiveAccessor(SMO_PRIM_FIELD_2_OFFSET, 1);
    primAccessors[2] = new DirectPrimitiveAccessor(SMO_PRIM_FIELD_3_OFFSET, 2);
    primAccessors[3] = new DirectPrimitiveAccessor(SMO_PRIM_FIELD_4_OFFSET, 3);
    primAccessors[4] = new DirectPrimitiveAccessor(SMO_PRIM_FIELD_5_OFFSET, 4);
    for (int i = SObject.NUM_PRIMITIVE_FIELDS; i < MAX_PRIM_FIELDS; i += 1) {
      primAccessors[i] = new ExtensionPrimitiveAccessor(i);
    }
  }

  public abstract static class AbstractObjectAccessor extends StorageAccessor {
    public abstract Object read(SObject obj);

    public abstract void write(SObject obj, Object value);
  }

  public static final class DirectObjectAccessor extends AbstractObjectAccessor {
    private final long fieldOffset;

    private DirectObjectAccessor(final long fieldOffset) {
      this.fieldOffset = fieldOffset;
    }

    @Override
    public Object read(final SObject obj) {
      return unsafe.getObject(obj, fieldOffset);
    }

    @Override
    public void write(final SObject obj, final Object value) {
      assert value != null;
      unsafe.putObject(obj, fieldOffset, value);
    }
  }

  public static final class ExtensionObjectAccessor extends AbstractObjectAccessor {
    private final int extensionIndex;

    private ExtensionObjectAccessor(final int fieldIdx) {
      this.extensionIndex = fieldIdx - SObject.NUM_OBJECT_FIELDS;
    }

    @Override
    public Object read(final SObject obj) {
      return obj.getExtensionObjFields()[extensionIndex];
    }

    @Override
    public void write(final SObject obj, final Object value) {
      obj.getExtensionObjFields()[extensionIndex] = value;
    }
  }

  public abstract static class AbstractPrimitiveAccessor extends StorageAccessor {
    protected final int fieldIsSetMask;

    private AbstractPrimitiveAccessor(final int fieldIdx) {
      this.fieldIsSetMask = SObject.getPrimitiveFieldMask(fieldIdx);
    }

    public abstract long readLong(SObject obj);

    public abstract double readDouble(SObject obj);

    public abstract void write(SObject obj, long value);

    public abstract void write(SObject obj, double value);

    public final boolean isPrimitiveSet(final SObject obj) {
      CompilerAsserts.neverPartOfCompilation("should probably use the one with profile");
      return (obj.primitiveUsedMap & fieldIsSetMask) != 0;
    }

    public final boolean isPrimitiveSet(final SObject obj,
        final IntValueProfile markProfile) {
      return (markProfile.profile(obj.primitiveUsedMap) & fieldIsSetMask) != 0;
    }

    public final void markPrimAsSet(final SObject obj) {
      CompilerAsserts.neverPartOfCompilation("should probably use the one with profile");
      obj.primitiveUsedMap |= fieldIsSetMask;
    }

    public final void markPrimAsSet(final SObject obj,
        final IntValueProfile markProfile) {
      obj.primitiveUsedMap = markProfile.profile(obj.primitiveUsedMap) | fieldIsSetMask;
    }
  }

  public static final class DirectPrimitiveAccessor extends AbstractPrimitiveAccessor {
    private final long offset;

    private DirectPrimitiveAccessor(final long fieldOffset, final int fieldIdx) {
      super(fieldIdx);
      offset = fieldOffset;
    }

    @Override
    public long readLong(final SObject obj) {
      return unsafe.getLong(obj, offset);
    }

    @Override
    public double readDouble(final SObject obj) {
      return unsafe.getDouble(obj, offset);
    }

    @Override
    public void write(final SObject obj, final long value) {
      unsafe.putLong(obj, offset, value);
    }

    @Override
    public void write(final SObject obj, final double value) {
      unsafe.putDouble(obj, offset, value);
    }
  }

  public static final class ExtensionPrimitiveAccessor extends AbstractPrimitiveAccessor {
    private final int extensionIndex;

    private ExtensionPrimitiveAccessor(final int fieldIdx) {
      super(fieldIdx);
      this.extensionIndex = fieldIdx - SObject.NUM_PRIMITIVE_FIELDS;
    }

    @Override
    public long readLong(final SObject obj) {
      return obj.getExtendedPrimFields()[extensionIndex];
    }

    @Override
    public double readDouble(final SObject obj) {
      return Double.longBitsToDouble(obj.getExtendedPrimFields()[extensionIndex]);
    }

    @Override
    public void write(final SObject obj, final long value) {
      obj.getExtendedPrimFields()[extensionIndex] = value;
    }

    @Override
    public void write(final SObject obj, final double value) {
      obj.getExtendedPrimFields()[extensionIndex] = Double.doubleToRawLongBits(value);
    }
  }
}
