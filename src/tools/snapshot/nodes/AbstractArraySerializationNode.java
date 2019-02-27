package tools.snapshot.nodes;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import som.primitives.ObjectPrims.ClassPrim;
import som.primitives.ObjectPrimsFactory.ClassPrimFactory;
import som.vm.constants.Classes;
import som.vmobjects.SArray;
import som.vmobjects.SArray.PartiallyEmptyArray;
import som.vmobjects.SClass;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.deserialization.FixupInformation;


public abstract class AbstractArraySerializationNode extends AbstractSerializationNode {

  private static final byte TYPE_BOOLEAN = 0;
  private static final byte TYPE_DOUBLE  = 1;
  private static final byte TYPE_LONG    = 2;
  private static final byte TYPE_OBJECT  = 3;
  private static final byte TYPE_EMPTY   = 4;

  private final SClass clazz;
  @Child ClassPrim     classPrim = ClassPrimFactory.create(null);

  public AbstractArraySerializationNode(final SClass clazz) {
    this.clazz = clazz;
  }

  @Override
  public Object deserialize(final DeserializationBuffer sb) {
    // This is for the DSL Processor, without this it won't generate stuff
    throw new UnsupportedOperationException("This should never be called");
  }

  @Specialization(guards = "sa.isBooleanType()")
  protected long doBoolean(final SArray sa, final SnapshotBuffer sb) {
    long location = getObjectLocation(sa, sb.getSnapshotVersion());
    if (location != -1) {
      return location;
    }

    boolean[] ba = sa.getBooleanStorage();
    int requiredSpace = ba.length;
    int start = sb.addObject(sa, clazz, requiredSpace + 5);
    int base = start;
    sb.putByteAt(base, TYPE_BOOLEAN);
    sb.putIntAt(base + 1, ba.length);
    base += 5;
    for (boolean b : ba) {
      sb.putByteAt(base, (byte) (b ? 1 : 0));
      base++;
    }
    return sb.calculateReferenceB(start);
  }

  @Specialization(guards = "sa.isDoubleType()")
  protected long doDouble(final SArray sa, final SnapshotBuffer sb) {
    long location = getObjectLocation(sa, sb.getSnapshotVersion());
    if (location != -1) {
      return location;
    }

    double[] da = sa.getDoubleStorage();
    int requiredSpace = da.length * Double.BYTES;
    int start = sb.addObject(sa, clazz, requiredSpace + 5);
    int base = start;
    sb.putByteAt(base, TYPE_DOUBLE);
    sb.putIntAt(base + 1, da.length);
    base += 5;
    for (double d : da) {
      sb.putDoubleAt(base, d);
      base += Double.BYTES;
    }
    return sb.calculateReferenceB(start);
  }

  @Specialization(guards = "sa.isLongType()")
  protected long doLong(final SArray sa, final SnapshotBuffer sb) {
    long location = getObjectLocation(sa, sb.getSnapshotVersion());
    if (location != -1) {
      return location;
    }

    long[] la = sa.getLongStorage();
    int requiredSpace = la.length * Long.BYTES;
    int start = sb.addObject(sa, clazz, requiredSpace + 5);
    int base = start;
    sb.putByteAt(base, TYPE_LONG);
    sb.putIntAt(base + 1, la.length);
    base += 5;
    for (long l : la) {
      sb.putLongAt(base, l);
      base += Long.BYTES;
    }
    return sb.calculateReferenceB(start);
  }

  @Specialization(guards = "sa.isObjectType()")
  protected long doObject(final SArray sa, final SnapshotBuffer sb) {
    long location = getObjectLocation(sa, sb.getSnapshotVersion());
    if (location != -1) {
      return location;
    }

    Object[] oa = sa.getObjectStorage();
    int requiredSpace = oa.length * 8;
    int start = sb.addObject(sa, clazz, requiredSpace + 5);
    int base = start;
    sb.putByteAt(base, TYPE_OBJECT);
    sb.putIntAt(base + 1, oa.length);
    base += 5;
    for (Object obj : oa) {
      long pos = classPrim.executeEvaluated(obj).serialize(obj, sb);
      sb.putLongAt(base, pos);
      base += Long.BYTES;
    }
    return sb.calculateReferenceB(start);
  }

  @Specialization(guards = "sa.isEmptyType()")
  protected long doEmpty(final SArray sa, final SnapshotBuffer sb) {
    long location = getObjectLocation(sa, sb.getSnapshotVersion());
    if (location != -1) {
      return location;
    }

    int start = sb.addObject(sa, clazz, 5);
    int base = start;
    sb.putByteAt(base, TYPE_EMPTY);
    sb.putIntAt(base + 1, sa.getEmptyStorage());
    return sb.calculateReferenceB(start);
  }

  @Specialization(guards = "sa.isPartiallyEmptyType()")
  protected long doPartiallyEmpty(final SArray sa, final SnapshotBuffer sb) {
    long location = getObjectLocation(sa, sb.getSnapshotVersion());
    if (location != -1) {
      return location;
    }

    PartiallyEmptyArray pea = sa.getPartiallyEmptyStorage();

    Object[] oa = pea.getStorage();
    int requiredSpace = oa.length * 8;
    int start = sb.addObject(sa, clazz, requiredSpace + 5);
    int base = start;
    sb.putByteAt(base, TYPE_OBJECT);
    sb.putIntAt(base + 1, oa.length);
    base += 5;
    for (Object obj : oa) {
      long pos = classPrim.executeEvaluated(obj).serialize(obj, sb);
      sb.putLongAt(base, pos);
      base += Long.BYTES;
    }

    return sb.calculateReferenceB(start);
  }

  protected Object parseBackingStorage(final DeserializationBuffer sb) {
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
          Object o = sb.getReference();
          if (DeserializationBuffer.needsFixup(o)) {
            sb.installFixup(new ArrayEntryFixup(oa, i));
          } else {
            oa[i] = o;
          }
        }
        backing = oa;
        break;
      case TYPE_EMPTY:
        backing = len;
        break;
      default:
        throw new IllegalArgumentException();
    }
    return backing;
  }

  @GenerateNodeFactory
  public abstract static class ArraySerializationNode extends AbstractArraySerializationNode {

    public ArraySerializationNode() {
      super(Classes.arrayClass);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      Object backing = parseBackingStorage(sb);
      return new SArray.SMutableArray(backing, Classes.arrayClass);
    }
  }

  @GenerateNodeFactory
  public abstract static class TransferArraySerializationNode
      extends AbstractArraySerializationNode {

    public TransferArraySerializationNode() {
      super(Classes.transferArrayClass);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      Object backing = parseBackingStorage(sb);
      return new SArray.STransferArray(backing, Classes.transferArrayClass);
    }
  }

  @GenerateNodeFactory
  public abstract static class ValueArraySerializationNode
      extends AbstractArraySerializationNode {

    public ValueArraySerializationNode() {
      super(Classes.valueArrayClass);
    }

    @Override
    public Object deserialize(final DeserializationBuffer sb) {
      Object backing = parseBackingStorage(sb);
      return new SArray.SImmutableArray(backing, Classes.valueArrayClass);
    }
  }

  public static class ArrayEntryFixup extends FixupInformation {
    Object[] args;
    int      idx;

    public ArrayEntryFixup(final Object[] args, final int idx) {
      this.args = args;
      this.idx = idx;
    }

    @Override
    public void fixUp(final Object o) {
      args[idx] = o;
    }
  }
}
