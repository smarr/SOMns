package tools.snapshot.nodes;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;


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
      int base = sb.addObject(o, Classes.stringClass, data.length + 4);
      sb.putIntAt(base, data.length);
      sb.putBytesAt(base + 4, data);
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
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
      int base = sb.addObject(o, Classes.integerClass, Long.BYTES);
      sb.putLongAt(base, l);
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
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
      int base = sb.addObject(o, Classes.doubleClass, Double.BYTES);
      sb.putDoubleAt(base, d);
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
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
      int base = sb.addObject(o, Classes.booleanClass, 1);
      sb.putByteAt(base, (byte) (b ? 1 : 0));
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
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
      sb.addObject(o, Classes.trueClass, 0);
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
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
      sb.addObject(o, Classes.falseClass, 0);
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
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
      int base = sb.addObject(o, Classes.symbolClass, 2);
      sb.putShortAt(base, ss.getSymbolId());
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      short symid = sb.getShort();
      return SnapshotBackend.getSymbolForId(symid);
    }
  }

  @GenerateNodeFactory
  public abstract static class ClassSerializationNode extends AbstractSerializationNode {

    public ClassSerializationNode(final SClass clazz) {
      super(clazz);
    }

    protected short getSymbolId(final SClass cls) {
      return Symbols.symbolFor(cls.getMixinDefinition().getIdentifier())
                    .getSymbolId();
    }

    @Specialization
    protected void doCached(final SClass cls, final SnapshotBuffer sb,
        @Cached("getSymbolId(clazz)") final short cachedId) {
      CompilerAsserts.compilationConstant(cachedId);
      int base = sb.addObject(cls, Classes.classClass, 2);
      sb.putShortAt(base, cachedId);
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      short id = sb.getShort();
      return SnapshotBackend.lookupClass(id);
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
      int base = sb.addObject(si, Classes.methodClass, Short.BYTES);
      sb.putShortAt(base, Symbols.symbolFor(si.getIdentifier()).getSymbolId());
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
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
      sb.addObject(o, Classes.nilClass, 0);
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      return Nil.nilObject;
    }
  }
}
