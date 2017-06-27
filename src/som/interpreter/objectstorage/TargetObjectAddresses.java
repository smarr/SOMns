package som.interpreter.objectstorage;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;

import som.vmobjects.SObject.SMutableObject;


@TargetClass(ObjectAddresses.class)
public final class TargetObjectAddresses {
  @RecomputeFieldValue(kind = Kind.FieldOffset, declClass = SMutableObject.class,
      name = "field1") //
  @Alias public static long field1Offset;

  @RecomputeFieldValue(kind = Kind.FieldOffset, declClass = SMutableObject.class,
      name = "field2") //
  @Alias public static long field2Offset;

  @RecomputeFieldValue(kind = Kind.FieldOffset, declClass = SMutableObject.class,
      name = "field3") //
  @Alias public static long field3Offset;

  @RecomputeFieldValue(kind = Kind.FieldOffset, declClass = SMutableObject.class,
      name = "field4") //
  @Alias public static long field4Offset;

  @RecomputeFieldValue(kind = Kind.FieldOffset, declClass = SMutableObject.class,
      name = "field5") //
  @Alias public static long field5Offset;

  @RecomputeFieldValue(kind = Kind.FieldOffset, declClass = SMutableObject.class,
      name = "primField1") //
  @Alias public static long prim1Offset;

  @RecomputeFieldValue(kind = Kind.FieldOffset, declClass = SMutableObject.class,
      name = "primField2") //
  @Alias public static long prim2Offset;

  @RecomputeFieldValue(kind = Kind.FieldOffset, declClass = SMutableObject.class,
      name = "primField3") //
  @Alias public static long prim3Offset;

  @RecomputeFieldValue(kind = Kind.FieldOffset, declClass = SMutableObject.class,
      name = "primField4") //
  @Alias public static long prim4Offset;

  @RecomputeFieldValue(kind = Kind.FieldOffset, declClass = SMutableObject.class,
      name = "primField5") //
  @Alias public static long prim5Offset;
}
