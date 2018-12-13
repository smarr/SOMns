package tools.snapshot.nodes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.actors.SFarReference;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActors.TracingActor;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.FixupInformation;


public abstract class PrimitiveSerializationNodes {
  @GenerateNodeFactory
  public abstract static class StringSerializationNode extends AbstractSerializationNode {

    public StringSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof String;
      String s = (String) o;

      byte[] data = s.getBytes(StandardCharsets.UTF_8);
      int base = sb.addObject(o, classFact, data.length + 4);
      sb.putIntAt(base, data.length);
      sb.putBytesAt(base + 4, data);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      int len = sb.getInt();
      byte[] b = new byte[len];
      sb.get(b);
      String s = new String(b, StandardCharsets.UTF_8);
      return s;
    }
  }

  @GenerateNodeFactory
  public abstract static class IntegerSerializationNode extends AbstractSerializationNode {

    public IntegerSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof Long;
      long l = (long) o;
      int base = sb.addObject(o, classFact, Long.BYTES);
      sb.putLongAt(base, l);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return sb.getLong();
    }
  }

  @GenerateNodeFactory
  public abstract static class DoubleSerializationNode extends AbstractSerializationNode {

    public DoubleSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof Double;
      double d = (double) o;
      int base = sb.addObject(o, classFact, Double.BYTES);
      sb.putDoubleAt(base, d);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return sb.getDouble();
    }
  }

  @GenerateNodeFactory
  public abstract static class BooleanSerializationNode extends AbstractSerializationNode {

    public BooleanSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof Boolean;
      boolean b = (boolean) o;
      int base = sb.addObject(o, classFact, 1);
      sb.putByteAt(base, (byte) (b ? 1 : 0));
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return sb.get() == 1;
    }
  }

  @GenerateNodeFactory
  public abstract static class TrueSerializationNode extends AbstractSerializationNode {

    public TrueSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof Boolean;
      assert ((boolean) o);
      sb.addObject(o, classFact, 0);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return true;
    }
  }

  @GenerateNodeFactory
  public abstract static class FalseSerializationNode extends AbstractSerializationNode {

    public FalseSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof Boolean;
      assert !((boolean) o);
      sb.addObject(o, classFact, 0);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return false;
    }
  }

  @GenerateNodeFactory
  public abstract static class SymbolSerializationNode extends AbstractSerializationNode {

    public SymbolSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof SSymbol;
      SSymbol ss = (SSymbol) o;
      int base = sb.addObject(o, classFact, 2);
      sb.putShortAt(base, ss.getSymbolId());
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      short symid = sb.getShort();
      return SnapshotBackend.getSymbolForId(symid);
    }
  }

  @GenerateNodeFactory
  public abstract static class ClassSerializationNode extends AbstractSerializationNode {

    public ClassSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    protected short getSymbolId(final SClass clazz) {
      return clazz.getMixinDefinition().getIdentifier().getSymbolId();
    }

    @Specialization
    protected void doCached(final SClass cls, final SnapshotBuffer sb,
        @Cached("getSymbolId(cls)") final short cachedId) {
      CompilerAsserts.compilationConstant(cachedId);
      int base = sb.addObject(cls, Classes.classClass.getFactory(), 2);
      sb.putShortAt(base, cachedId);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      short id = sb.getShort();
      return SnapshotBackend.lookupClass(id);
    }
  }

  @GenerateNodeFactory
  public abstract static class SInvokableSerializationNode extends AbstractSerializationNode {

    public SInvokableSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof SInvokable;
      SInvokable si = (SInvokable) o;
      int base = sb.addObject(si, classFact, Short.BYTES);
      sb.putShortAt(base, si.getIdentifier().getSymbolId());
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      short id = sb.getShort();
      SSymbol s = SnapshotBackend.getSymbolForId(id);
      return SnapshotBackend.lookupInvokable(s);
    }
  }

  @GenerateNodeFactory
  public abstract static class NilSerializationNode extends AbstractSerializationNode {

    public NilSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      sb.addObject(o, classFact, 0);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  public abstract static class FarRefSerializationNode extends AbstractSerializationNode {

    public FarRefSerializationNode(final ClassFactory classFact) {
      super(classFact);
    }

    @Specialization
    public void serialize(final SFarReference o, final SnapshotBuffer sb) {
      int base = sb.addObject(o, classFact, Integer.BYTES + Long.BYTES);
      TracingActor other = (TracingActor) o.getActor();
      sb.putIntAt(base, other.getActorId());

      // writing the reference is done through this method.
      // actual writing may happen at a later point in time if the object wasn't serialized
      // yetD
      other.getSnapshotRecord().farReference(o.getValue(), sb, base + Integer.BYTES);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      TracingActor other = (TracingActor) SnapshotBackend.lookupActor(sb.getInt());
      DeserializationBuffer otherDB = other.getDeserializationBuffer();

      Object value = otherDB.getReference();
      SFarReference result = new SFarReference(other, value);

      if (DeserializationBuffer.needsFixup(value)) {
        otherDB.installFixup(new FarRefFixupInformation(result));
      }

      return result;
    }

    private static final class FarRefFixupInformation extends FixupInformation {
      SFarReference ref;

      FarRefFixupInformation(final SFarReference ref) {
        this.ref = ref;
      }

      @Override
      public void fixUp(final Object o) {
        // This may be an alternative to making final fields non-final.
        // Only replay executions would be affected by this.
        try {
          Field field = SFarReference.class.getField("value");
          Field modifiersField = Field.class.getDeclaredField("modifiers");
          modifiersField.setAccessible(true);
          modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
          field.set(ref, o);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
