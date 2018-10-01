package tools.snapshot.nodes;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.interpreter.Types;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SClass;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.nodes.AbstractArraySerializationNodeGen.ArraySerializationNodeFactory;
import tools.snapshot.nodes.AbstractArraySerializationNodeGen.TransferArraySerializationNodeFactory;
import tools.snapshot.nodes.AbstractArraySerializationNodeGen.ValueArraySerializationNodeFactory;


public abstract class AbstractArraySerializationNode extends AbstractSerializationNode {
  private static final byte TYPE_BOOLEAN = 0;
  private static final byte TYPE_DOUBLE  = 1;
  private static final byte TYPE_LONG    = 2;
  private static final byte TYPE_OBJECT  = 3;
  private static final byte TYPE_EMPTY   = 4;

  @Override
  public void serialize(final Object o, final SnapshotBuffer sb) {
    assert o instanceof SArray;
    SArray sa = (SArray) o;
    this.execute(sa, sb);
  }

  protected abstract void execute(SArray sa, SnapshotBuffer sb);

  @Override
  public Object deserialize(final ByteBuffer sb) {
    // This is for the DSL Processor, without this it won't generate stuff
    throw new UnsupportedOperationException("This should never be called");
  }

  @Specialization(guards = "sa.isBooleanType()")
  protected void doBoolean(final SArray sa, final SnapshotBuffer sb) {
    boolean[] ba = sa.getBooleanStorage();
    int requiredSpace = ba.length;
    int base = sb.addObject(sa, sa.getSOMClass(), requiredSpace + 5);
    sb.putByteAt(base, TYPE_BOOLEAN);
    sb.putIntAt(base + 1, ba.length);
    base += 5;
    for (boolean b : ba) {
      sb.putByteAt(base, (byte) (b ? 1 : 0));
      base++;
    }
  }

  @Specialization(guards = "sa.isDoubleType()")
  protected void doDouble(final SArray sa, final SnapshotBuffer sb) {
    double[] da = sa.getDoubleStorage();
    int requiredSpace = da.length * Double.BYTES;
    int base = sb.addObject(sa, sa.getSOMClass(), requiredSpace + 5);
    sb.putByteAt(base, TYPE_DOUBLE);
    sb.putIntAt(base + 1, da.length);
    base += 5;
    for (double d : da) {
      sb.putDoubleAt(base, d);
      base += Double.BYTES;
    }
  }

  @Specialization(guards = "sa.isLongType()")
  protected void doLong(final SArray sa, final SnapshotBuffer sb) {
    long[] la = sa.getLongStorage();
    int requiredSpace = la.length * Long.BYTES;
    int base = sb.addObject(sa, sa.getSOMClass(), requiredSpace + 5);
    sb.putByteAt(base, TYPE_LONG);
    sb.putIntAt(base + 1, la.length);
    base += 5;
    for (long l : la) {
      sb.putLongAt(base, l);
      base += Long.BYTES;
    }
  }

  @Specialization(guards = "sa.isObjectType()")
  protected void doObject(final SArray sa, final SnapshotBuffer sb) {
    Object[] oa = sa.getObjectStorage();
    int requiredSpace = oa.length * 8;
    int base = sb.addObject(sa, sa.getSOMClass(), requiredSpace + 5);
    sb.putByteAt(base, TYPE_OBJECT);
    sb.putIntAt(base + 1, oa.length);
    base += 5;
    for (Object obj : oa) {
      Types.getClassOf(obj).serialize(obj, sb);
      long pos = sb.getObjectPointer(obj);
      sb.putLongAt(base, pos);
      base += Long.BYTES;
    }
  }

  @Specialization(guards = "sa.isEmptyType()")
  protected void doEmpty(final SArray sa, final SnapshotBuffer sb) {
    int base = sb.addObject(sa, sa.getSOMClass(), 5);
    sb.putByteAt(base, TYPE_EMPTY);
    sb.putIntAt(base + 1, sa.getEmptyStorage());
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

  @GenerateNodeFactory
  public abstract static class ArraySerializationNode extends AbstractArraySerializationNode {

    public static ArraySerializationNode create(final SClass clazz) {
      return ArraySerializationNodeFactory.create();
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      Object backing = parseBackingStorage(sb);
      return new SArray.SMutableArray(backing, Classes.arrayClass);
    }
  }

  @GenerateNodeFactory
  public abstract static class TransferArraySerializationNode extends ArraySerializationNode {

    public static TransferArraySerializationNode create(final SClass clazz) {
      return TransferArraySerializationNodeFactory.create();
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      Object backing = parseBackingStorage(sb);
      return new SArray.STransferArray(backing, Classes.transferArrayClass);
    }
  }

  @GenerateNodeFactory
  public abstract static class ValueArraySerializationNode extends ArraySerializationNode {

    public static ValueArraySerializationNode create(final SClass clazz) {
      return ValueArraySerializationNodeFactory.create();
    }

    @Override
    public Object deserialize(final ByteBuffer sb) {
      Object backing = parseBackingStorage(sb);
      return new SArray.SImmutableArray(backing, Classes.valueArrayClass);
    }
  }
}
