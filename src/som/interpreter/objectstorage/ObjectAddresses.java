package som.interpreter.objectstorage;

import java.lang.reflect.Field;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;

import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;


public final class ObjectAddresses {

  public static long field1Offset;
  public static long field2Offset;
  public static long field3Offset;
  public static long field4Offset;
  public static long field5Offset;

  public static long prim1Offset;
  public static long prim2Offset;
  public static long prim3Offset;
  public static long prim4Offset;
  public static long prim5Offset;

  public static long field1OffsetImm;
  public static long field2OffsetImm;
  public static long field3OffsetImm;
  public static long field4OffsetImm;
  public static long field5OffsetImm;

  public static long prim1OffsetImm;
  public static long prim2OffsetImm;
  public static long prim3OffsetImm;
  public static long prim4OffsetImm;
  public static long prim5OffsetImm;

  @AutomaticFeature
  private static class AotFeature implements Feature {
    @Override
    public void beforeAnalysis(final BeforeAnalysisAccess baa) {
      try {
        for (int i = 1; i <= 5; i += 1) {
          Field f = SMutableObject.class.getDeclaredField("field" + i);
          baa.registerAsUnsafeAccessed(f);
        }

        for (int i = 1; i <= 5; i += 1) {
          Field f = SMutableObject.class.getDeclaredField("primField" + i);
          baa.registerAsUnsafeAccessed(f);
        }

        for (int i = 1; i <= 5; i += 1) {
          Field f = SImmutableObject.class.getDeclaredField("field" + i);
          baa.registerAsUnsafeAccessed(f);
        }

        for (int i = 1; i <= 5; i += 1) {
          Field f = SImmutableObject.class.getDeclaredField("primField" + i);
          baa.registerAsUnsafeAccessed(f);
        }
      } catch (NoSuchFieldException | SecurityException e) {
        throw new RuntimeException(e);
      }
    }
  }

}
