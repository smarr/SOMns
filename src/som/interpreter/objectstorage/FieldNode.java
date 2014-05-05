package som.interpreter.objectstorage;

import som.interpreter.TruffleCompiler;
import som.interpreter.TypesGen;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.vm.Universe;
import som.vmobjects.SObject;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;


public abstract class FieldNode extends Node {
  protected final int fieldIndex;

  public FieldNode(final int fieldIndex) {
    this.fieldIndex = fieldIndex;
  }

  public final int getFieldIndex() {
    return fieldIndex;
  }

  public abstract static class AbstractReadFieldNode extends FieldNode {
    public AbstractReadFieldNode(final int fieldIndex) {
      super(fieldIndex);
    }

    public boolean isSet(final SObject obj) {
      return true;
    }

    public abstract Object read(SObject obj);

    public long readLong(final SObject obj) throws UnexpectedResultException {
      return TypesGen.TYPES.expectLong(read(obj));
    }

    public double readDouble(final SObject obj) throws UnexpectedResultException {
      return TypesGen.TYPES.expectDouble(read(obj));
    }

    public Object respecializeAndRead(final SObject obj, final String reason) {
      obj.updateLayoutToMatchClass();

      final ObjectLayout    layout   = obj.getObjectLayout();
      final StorageLocation location = layout.getStorageLocation(fieldIndex);

      AbstractReadFieldNode newNode = location.getReadNode(fieldIndex, layout);
      return replace(newNode, reason).read(obj);
    }
  }

  public static final class UninitializedReadFieldNode extends AbstractReadFieldNode {

    public UninitializedReadFieldNode(final int fieldIndex) {
      super(fieldIndex);
    }

    @Override
    public Object read(final SObject obj) {
      return respecializeAndRead(obj, "uninitalized node");
    }
  }

  public abstract static class ReadSpecializedFieldNode extends AbstractReadFieldNode {
    protected final ObjectLayout layout;

    public ReadSpecializedFieldNode(final int fieldIndex, final ObjectLayout layout) {
      super(fieldIndex);
      this.layout = layout;
    }

    protected final boolean hasExpectedLayout(final SObject obj) {
      return layout == obj.getObjectLayout();
    }
  }

  public static final class ReadUnwrittenFieldNode extends ReadSpecializedFieldNode {
    private final SObject nilObject;

    public ReadUnwrittenFieldNode(final int fieldIndex, final ObjectLayout layout) {
      super(fieldIndex, layout);
      nilObject = Universe.current().nilObject;
    }

    @Override
    public boolean isSet(final SObject obj) {
      if (hasExpectedLayout(obj)) {
        return false;
      } else {
        return true;
      }
    }

    @Override
    public Object read(final SObject obj) {
      if (hasExpectedLayout(obj)) {
        return nilObject;
      } else {
        return respecializeAndRead(obj, "unexpected object layout");
      }
    }
  }

  public static final class ReadLongFieldNode extends ReadSpecializedFieldNode {
    private final LongStorageLocation storage;

    public ReadLongFieldNode(final int fieldIndex, final ObjectLayout layout) {
      super(fieldIndex, layout);
      this.storage = (LongStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public long readLong(final SObject obj) throws UnexpectedResultException {
      boolean assumption = hasExpectedLayout(obj);
      if (assumption) {
        return storage.readLong(obj, assumption);
      } else {
        return TypesGen.TYPES.expectLong(respecializeAndRead(obj, "unexpected object layout"));
      }
    }

    @Override
    public Object read(final SObject obj) {
      try {
        return readLong(obj);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }
  }

  public static final class ReadDoubleFieldNode extends ReadSpecializedFieldNode {
    private final DoubleStorageLocation storage;

    public ReadDoubleFieldNode(final int fieldIndex, final ObjectLayout layout) {
      super(fieldIndex, layout);
      this.storage = (DoubleStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public double readDouble(final SObject obj) throws UnexpectedResultException {
      boolean assumption = hasExpectedLayout(obj);
      if (assumption) {
        return storage.readDouble(obj, assumption);
      } else {
        return TypesGen.TYPES.expectDouble(respecializeAndRead(obj, "unexpected object layout"));
      }
    }

    @Override
    public Object read(final SObject obj) {
      try {
        return readLong(obj);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }
  }

  public static final class ReadObjectFieldNode extends ReadSpecializedFieldNode {
    private final AbstractObjectStorageLocation storage;

    public ReadObjectFieldNode(final int fieldIndex, final ObjectLayout layout) {
      super(fieldIndex, layout);
      this.storage = (AbstractObjectStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public Object read(final SObject obj) {
      boolean assumption = hasExpectedLayout(obj);
      if (assumption) {
        return storage.read(obj, assumption);
      } else {
        return respecializeAndRead(obj, "unexpected object layout");
      }
    }
  }

  public abstract static class AbstractWriteFieldNode extends FieldNode {
    public AbstractWriteFieldNode(final int fieldIndex) {
      super(fieldIndex);
    }

    public abstract Object write(SObject obj, Object value);

    public long write(final SObject obj, final long value) {
      write(obj, (Object) value);
      return value;
    }

    public double write(final SObject obj, final double value) {
      write(obj, (Object) value);
      return value;
    }

    public void writeAndRespecialize(final SObject obj, final Object value,
        final String reason) {
      TruffleCompiler.transferToInterpreterAndInvalidate(reason);

      obj.setField(fieldIndex, value);

      final ObjectLayout layout = obj.getObjectLayout();
      final StorageLocation location = layout.getStorageLocation(fieldIndex);
      AbstractWriteFieldNode newNode = location.getWriteNode(fieldIndex, layout);
      replace(newNode, reason);
    }
  }

  public static final class UninitializedWriteFieldNode extends AbstractWriteFieldNode {
    public UninitializedWriteFieldNode(final int fieldIndex) {
      super(fieldIndex);
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      writeAndRespecialize(obj, value, "initialize write field node");
      return value;
    }
  }

  private abstract static class WriteSpecializedFieldNode extends AbstractWriteFieldNode {

    protected final ObjectLayout layout;

    public WriteSpecializedFieldNode(final int fieldIndex, final ObjectLayout layout) {
      super(fieldIndex);
      this.layout = layout;
    }

    protected final boolean hasExpectedLayout(final SObject obj) {
      return layout == obj.getObjectLayout();
    }
  }

  public static final class WriteLongFieldNode extends WriteSpecializedFieldNode {
    private final LongStorageLocation storage;

    public WriteLongFieldNode(final int fieldIndex, final ObjectLayout layout) {
      super(fieldIndex, layout);
      this.storage = (LongStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public long write(final SObject obj, final long value) {
      if (hasExpectedLayout(obj)) {
        storage.writeLong(obj, value);
      } else {
        writeAndRespecialize(obj, value, "unexpected object layout");
      }
      return value;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Long) {
        return write(obj, (long) value);
      } else {
        writeAndRespecialize(obj, value, "unexpected value");
        return value;
      }
    }
  }

  public static final class WriteDoubleFieldNode extends WriteSpecializedFieldNode {
    private final DoubleStorageLocation storage;

    public WriteDoubleFieldNode(final int fieldIndex, final ObjectLayout layout) {
      super(fieldIndex, layout);
      this.storage = (DoubleStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public double write(final SObject obj, final double value) {
      if (hasExpectedLayout(obj)) {
        storage.writeDouble(obj, value);
      } else {
        writeAndRespecialize(obj, value, "unexpected object layout");
      }
      return value;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Double) {
        return write(obj, (double) value);
      } else {
        writeAndRespecialize(obj, value, "unexpected value");
        return value;
      }
    }
  }

  public static final class WriteObjectFieldNode extends WriteSpecializedFieldNode {
    private final AbstractObjectStorageLocation storage;

    public WriteObjectFieldNode(final int fieldIndex, final ObjectLayout layout) {
      super(fieldIndex, layout);
      this.storage = (AbstractObjectStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (hasExpectedLayout(obj)) {
        storage.write(obj, value);
      } else {
        writeAndRespecialize(obj, value, "unexpected object layout");
      }
      return value;
    }
  }
}
