package tools.snapshot.nodes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.actors.SFarReference;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.concurrency.TracingActors.ReplayActor;
import tools.concurrency.TracingActors.TracingActor;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.FixupInformation;
import tools.snapshot.deserialization.SnapshotParser;


public abstract class PrimitiveSerializationNodes {
  @GenerateNodeFactory
  public abstract static class StringSerializationNode extends AbstractSerializationNode {

    public StringSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof String;
      String s = (String) o;

      byte[] data = s.getBytes(StandardCharsets.UTF_8);
      int base = sb.addObject(o, clazz, data.length + 4);
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

    public IntegerSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof Long;
      long l = (long) o;
      int base = sb.addObject(o, clazz, Long.BYTES);
      sb.putLongAt(base, l);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return sb.getLong();
    }
  }

  @GenerateNodeFactory
  public abstract static class DoubleSerializationNode extends AbstractSerializationNode {

    public DoubleSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof Double;
      double d = (double) o;
      int base = sb.addObject(o, clazz, Double.BYTES);
      sb.putDoubleAt(base, d);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return sb.getDouble();
    }
  }

  @GenerateNodeFactory
  public abstract static class BooleanSerializationNode extends AbstractSerializationNode {

    public BooleanSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof Boolean;
      boolean b = (boolean) o;
      int base = sb.addObject(o, clazz, 1);
      sb.putByteAt(base, (byte) (b ? 1 : 0));
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return sb.get() == 1;
    }
  }

  @GenerateNodeFactory
  public abstract static class TrueSerializationNode extends AbstractSerializationNode {

    public TrueSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof Boolean;
      assert ((boolean) o);
      sb.addObject(o, clazz, 0);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return true;
    }
  }

  @GenerateNodeFactory
  public abstract static class FalseSerializationNode extends AbstractSerializationNode {

    public FalseSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof Boolean;
      assert !((boolean) o);
      sb.addObject(o, clazz, 0);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return false;
    }
  }

  @GenerateNodeFactory
  public abstract static class SymbolSerializationNode extends AbstractSerializationNode {

    public SymbolSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof SSymbol;
      SSymbol ss = (SSymbol) o;
      int base = sb.addObject(o, clazz, 2);
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

    public ClassSerializationNode(final SClass clazz) {
      super(clazz);
    }

    protected int getSymbolId(final SClass clazzz) {
      return clazzz.getIdentity();
    }

    @Specialization
    protected void doCached(final SClass cls, final SnapshotBuffer sb,
        @Cached("getSymbolId(cls)") final int cachedId) {
      CompilerAsserts.compilationConstant(cachedId);
      int base = sb.addObject(cls, Classes.classClass, Integer.BYTES);
      sb.putIntAt(base, cachedId);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      int id = sb.getInt();
      // If lookup yields null we need to fixup in caller
      return SnapshotBackend.lookupClass(id, sb.position());
    }
  }

  @GenerateNodeFactory
  public abstract static class SInvokableSerializationNode extends AbstractSerializationNode {

    public SInvokableSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof SInvokable;
      SInvokable si = (SInvokable) o;
      int base = sb.addObject(si, clazz, Short.BYTES);
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

    public NilSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final Object o, final SnapshotBuffer sb) {
      sb.addObject(o, clazz, 0);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  public abstract static class FarRefSerializationNode extends AbstractSerializationNode {

    public FarRefSerializationNode(final SClass clazz) {
      super(clazz);
    }

    @Specialization
    public void serialize(final SFarReference o, final SnapshotBuffer sb) {
      int base = sb.addObject(o, clazz, Integer.BYTES + Long.BYTES);
      TracingActor other = (TracingActor) o.getActor();
      sb.putIntAt(base, other.getActorId());

      // writing the reference is done through this method.
      // actual writing may happen at a later point in time if the object wasn't serialized
      // yetD
      other.getSnapshotRecord().farReference(o.getValue(), sb, base + Integer.BYTES);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      int actorId = sb.getInt();
      TracingActor other = (TracingActor) SnapshotBackend.lookupActor(actorId);
      if (other == null) {
        // no messages recorded for this actor, need to create it here.
        other = ReplayActor.getActorWithId(actorId);
      }

      TracingActor current = SnapshotBackend.getCurrentActor();
      SnapshotParser.setCurrentActor((ReplayActor) other);
      Object value = sb.getReference();
      SnapshotParser.setCurrentActor((ReplayActor) current);

      SFarReference result = new SFarReference(other, value);

      if (DeserializationBuffer.needsFixup(value)) {
        sb.installFixup(new FarRefFixupInformation(result));
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
