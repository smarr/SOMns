package tools.snapshot.nodes;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;


public abstract class PrimitiveSerializationNodes {
  public static class StringSerializationNode extends AbstractSerializationNode {
    @Override
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

  public static class IntegerSerializationNode extends AbstractSerializationNode {
    @Override
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

  public static class DoubleSerializationNode extends AbstractSerializationNode {
    @Override
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

  public static class BooleanSerializationNode extends AbstractSerializationNode {
    @Override
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

  public static class TrueSerializationNode extends AbstractSerializationNode {
    @Override
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

  public static class FalseSerializationNode extends AbstractSerializationNode {
    @Override
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

  public static class SymbolSerializationNode extends AbstractSerializationNode {
    @Override
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

  public static class ClassSerializationNode extends AbstractSerializationNode {
    @Override
    public void serialize(final Object o, final SnapshotBuffer sb) {
      assert o instanceof SClass;
      SClass sc = (SClass) o;
      int base = sb.addObject(o, Classes.classClass, 2);
      sb.putShortAt(base,
          Symbols.symbolFor(sc.getMixinDefinition().getIdentifier()).getSymbolId());
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      short id = sb.getShort();
      return SnapshotBackend.lookupClass(id);
    }
  }

  public static class SInvokableSerializationNode extends AbstractSerializationNode {
    @Override
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

  public static class NilSerializationNode extends AbstractSerializationNode {
    @Override
    public void serialize(final Object o, final SnapshotBuffer sb) {
      sb.addObject(o, Classes.nilClass, 0);
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      return Nil.nilObject;
    }
  }
}
