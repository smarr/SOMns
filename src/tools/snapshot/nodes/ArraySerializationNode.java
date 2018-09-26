package tools.snapshot.nodes;

import java.nio.ByteBuffer;

import som.interpreter.Types;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import tools.snapshot.SnapshotBuffer;


public class ArraySerializationNode extends AbstractSerializationNode {
  private static final byte TYPE_BOOLEAN = 0;
  private static final byte TYPE_DOUBLE  = 1;
  private static final byte TYPE_LONG    = 2;
  private static final byte TYPE_OBJECT  = 3;
  private static final byte TYPE_EMPTY   = 4;

  @Override
  public void serialize(final Object o, final SnapshotBuffer sb) {
    assert o instanceof SArray;
    SArray sa = (SArray) o;
    serializeStorage(sa, sb);
  }

  protected void serializeStorage(final SArray sa, final SnapshotBuffer sb) {
    int requiredSpace;
    if (sa.isBooleanType()) {
      boolean[] ba = sa.getBooleanStorage();
      requiredSpace = ba.length;
      int base = sb.addObject(sa, sa.getSOMClass(), requiredSpace + 5);
      sb.putByteAt(base, TYPE_BOOLEAN);
      sb.putIntAt(base + 1, ba.length);
      base += 5;
      for (boolean b : ba) {
        sb.putByteAt(base, (byte) (b ? 1 : 0));
        base++;
      }
    } else if (sa.isDoubleType()) {
      double[] da = sa.getDoubleStorage();
      requiredSpace = da.length * Double.BYTES;
      int base = sb.addObject(sa, sa.getSOMClass(), requiredSpace + 5);
      sb.putByteAt(base, TYPE_DOUBLE);
      sb.putIntAt(base + 1, da.length);
      base += 5;
      for (double d : da) {
        sb.putDoubleAt(base, d);
        base += Double.BYTES;
      }
    } else if (sa.isLongType()) {
      long[] la = sa.getLongStorage();
      requiredSpace = la.length * Long.BYTES;
      int base = sb.addObject(sa, sa.getSOMClass(), requiredSpace + 5);
      sb.putByteAt(base, TYPE_LONG);
      sb.putIntAt(base + 1, la.length);
      base += 5;
      for (long l : la) {
        sb.putLongAt(base, l);
        base += Long.BYTES;
      }
    } else if (sa.isObjectType()) {
      Object[] oa = sa.getObjectStorage();
      requiredSpace = oa.length * 8;
      int base = sb.addObject(sa, sa.getSOMClass(), requiredSpace + 5);
      sb.putByteAt(base, TYPE_OBJECT);
      sb.putIntAt(base + 1, oa.length);
      base += 5;
      for (Object obj : oa) {
        Types.getClassOf(obj).getSerializer().serialize(obj, sb);
        long pos = sb.getObjectPointer(obj);
        sb.putLongAt(base, pos);
        base += Long.BYTES;
      }
    } else if (sa.isEmptyType()) {
      int base = sb.addObject(sa, sa.getSOMClass(), 5);
      sb.putByteAt(base, TYPE_EMPTY);
      sb.putIntAt(base + 1, sa.getEmptyStorage());
    }
  }

  protected Object parseBackingStorage(final ByteBuffer sb) {
    byte type = sb.get();
    int len = sb.getInt();

    Object backing = null;

    switch (type) {
      case TYPE_BOOLEAN:
        boolean[] ba = new boolean[len];
        for (int i = 0; i < len; i++) {
          ba[i] = sb.get() == 1;
        }
        backing = ba;
        break;
      case TYPE_DOUBLE:
        double[] da = new double[len];
        for (int i = 0; i < len; i++) {
          da[i] = sb.getDouble();
        }
        backing = da;
        break;
      case TYPE_LONG:
        long[] la = new long[len];
        for (int i = 0; i < len; i++) {
          la[i] = sb.getLong();
        }
        backing = la;
        break;
      case TYPE_OBJECT:
        Object[] oa = new Object[len];
        for (int i = 0; i < len; i++) {
          oa[i] = deserializeReference(sb);
        }
        backing = oa;
        break;
      case TYPE_EMPTY:
        break;
      default:
        throw new IllegalArgumentException();
    }
    return backing;
  }

  @Override
  public Object deserialize(final ByteBuffer sb) {
    Object backing = parseBackingStorage(sb);
    return new SArray.SMutableArray(backing, Classes.arrayClass);
  }

  public static class TransferArraySerializationNode extends ArraySerializationNode {
    @Override
    public Object deserialize(final ByteBuffer sb) {
      Object backing = parseBackingStorage(sb);
      return new SArray.STransferArray(backing, Classes.transferArrayClass);
    }
  }

  public static class ValueArraySerializationNode extends ArraySerializationNode {
    @Override
    public Object deserialize(final ByteBuffer sb) {
      Object backing = parseBackingStorage(sb);
      return new SArray.SImmutableArray(backing, Classes.valueArrayClass);
    }
  }
}
