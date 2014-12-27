package som.interpreter.objectstorage;

import som.interpreter.TruffleCompiler;
import som.interpreter.TypesGen;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.vm.constants.Nil;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;


public abstract class FieldAccessorNode extends Node {
  protected final int fieldIndex;

  public static AbstractReadFieldNode createRead(final int fieldIndex) {
    return new UninitializedReadFieldNode(fieldIndex);
  }

  public static AbstractWriteFieldNode createWrite(final int fieldIndex) {
    return new UninitializedWriteFieldNode(fieldIndex);
  }

  private FieldAccessorNode(final int fieldIndex) {
    this.fieldIndex = fieldIndex;
  }

  public final int getFieldIndex() {
    return fieldIndex;
  }

  public abstract static class AbstractReadFieldNode extends FieldAccessorNode {
    public AbstractReadFieldNode(final int fieldIndex) {
      super(fieldIndex);
    }

    public abstract Object read(SObject obj);

    public long readLong(final SObject obj) throws UnexpectedResultException {
      return TypesGen.TYPES.expectLong(read(obj));
    }

    public double readDouble(final SObject obj) throws UnexpectedResultException {
      return TypesGen.TYPES.expectDouble(read(obj));
    }

    protected final Object specializeAndRead(final SObject obj, final String reason, final AbstractReadFieldNode next) {
      return specialize(obj, reason, next).read(obj);
    }

    protected final AbstractReadFieldNode specialize(final SObject obj,
        final String reason, final AbstractReadFieldNode next) {
      TruffleCompiler.transferToInterpreterAndInvalidate(reason);
      obj.updateLayoutToMatchClass();

      final ObjectLayout    layout   = obj.getObjectLayout();
      final StorageLocation location = layout.getStorageLocation(fieldIndex);

      AbstractReadFieldNode newNode = location.getReadNode(fieldIndex, layout, next);
      return replace(newNode, reason);
    }
  }

  public static final class UninitializedReadFieldNode extends AbstractReadFieldNode {

    public UninitializedReadFieldNode(final int fieldIndex) {
      super(fieldIndex);
    }

    @Override
    public Object read(final SObject obj) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return specializeAndRead(obj, "uninitalized node", new UninitializedReadFieldNode(fieldIndex));
    }
  }

  public abstract static class ReadSpecializedFieldNode extends AbstractReadFieldNode {
    protected final ObjectLayout layout;
    @Child private AbstractReadFieldNode nextInCache;

    public ReadSpecializedFieldNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(fieldIndex);
      this.layout = layout;
      nextInCache = next;
    }

    protected final boolean hasExpectedLayout(final SObject obj) {
      return layout == obj.getObjectLayout();
    }

    protected final AbstractReadFieldNode respecializedNodeOrNext(final SObject obj) {
      if (layout.layoutForSameClass(obj.getObjectLayout())) {
        return specialize(obj, "update outdated read node", nextInCache);
      } else {
        return nextInCache;
      }
    }
  }

  public static final class ReadUnwrittenFieldNode extends ReadSpecializedFieldNode {
    public ReadUnwrittenFieldNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(fieldIndex, layout, next);
    }

    @Override
    public Object read(final SObject obj) {
      if (hasExpectedLayout(obj)) {
        return Nil.nilObject;
      } else {
        return respecializedNodeOrNext(obj).read(obj);
      }
    }
  }

  public static final class ReadLongFieldNode extends ReadSpecializedFieldNode {
    private final LongStorageLocation storage;

    public ReadLongFieldNode(final int fieldIndex, final ObjectLayout layout,
        final AbstractReadFieldNode next) {
      super(fieldIndex, layout, next);
      this.storage = (LongStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public long readLong(final SObject obj) throws UnexpectedResultException {
      boolean assumption = hasExpectedLayout(obj);
      if (assumption) {
        return storage.readLong(obj, assumption);
      } else {
        return respecializedNodeOrNext(obj).readLong(obj);
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

    public ReadDoubleFieldNode(final int fieldIndex, final ObjectLayout layout,
        final AbstractReadFieldNode next) {
      super(fieldIndex, layout, next);
      this.storage = (DoubleStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public double readDouble(final SObject obj) throws UnexpectedResultException {
      boolean assumption = hasExpectedLayout(obj);
      if (assumption) {
        return storage.readDouble(obj, assumption);
      } else {
        return respecializedNodeOrNext(obj).readDouble(obj);
      }
    }

    @Override
    public Object read(final SObject obj) {
      try {
        return readDouble(obj);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }
  }

  public static final class ReadObjectFieldNode extends ReadSpecializedFieldNode {
    private final AbstractObjectStorageLocation storage;

    public ReadObjectFieldNode(final int fieldIndex, final ObjectLayout layout,
        final AbstractReadFieldNode next) {
      super(fieldIndex, layout, next);
      this.storage = (AbstractObjectStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public Object read(final SObject obj) {
      boolean assumption = hasExpectedLayout(obj);
      if (assumption) {
        return storage.read(obj, assumption);
      } else {
        return respecializedNodeOrNext(obj).read(obj);
      }
    }
  }

  public abstract static class AbstractWriteFieldNode extends FieldAccessorNode {
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

    protected final void writeAndRespecialize(final SObject obj, final Object value,
        final String reason, final AbstractWriteFieldNode next) {
      TruffleCompiler.transferToInterpreterAndInvalidate(reason);

      obj.setField(fieldIndex, value);

      final ObjectLayout layout = obj.getObjectLayout();
      final StorageLocation location = layout.getStorageLocation(fieldIndex);
      AbstractWriteFieldNode newNode = location.getWriteNode(fieldIndex, layout, next);
      replace(newNode, reason);
    }
  }

  public static final class UninitializedWriteFieldNode extends AbstractWriteFieldNode {
    public UninitializedWriteFieldNode(final int fieldIndex) {
      super(fieldIndex);
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      writeAndRespecialize(obj, value, "initialize write field node",
          new UninitializedWriteFieldNode(fieldIndex));
      return value;
    }
  }

  private abstract static class WriteSpecializedFieldNode extends AbstractWriteFieldNode {

    protected final ObjectLayout layout;
    @Child protected AbstractWriteFieldNode nextInCache;

    public WriteSpecializedFieldNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      super(fieldIndex);
      this.layout = layout;
      nextInCache = next;
    }

    protected final boolean hasExpectedLayout(final SObject obj) {
      return layout == obj.getObjectLayout();
    }
  }

  public static final class WriteLongFieldNode extends WriteSpecializedFieldNode {
    private final LongStorageLocation storage;

    public WriteLongFieldNode(final int fieldIndex, final ObjectLayout layout,
        final AbstractWriteFieldNode next) {
      super(fieldIndex, layout, next);
      this.storage = (LongStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public long write(final SObject obj, final long value) {
      if (hasExpectedLayout(obj)) {
        storage.writeLong(obj, value);
      } else {
        if (layout.layoutForSameClass(obj.getObjectLayout())) {
          writeAndRespecialize(obj, value, "update outdated write node", nextInCache);
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Long) {
        write(obj, (long) value);
      } else {
        if (layout.layoutForSameClass(obj.getObjectLayout())) {
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache);
        } else {
          nextInCache.write(obj, (long) value);
        }
      }
      return value;
    }
  }

  public static final class WriteDoubleFieldNode extends WriteSpecializedFieldNode {
    private final DoubleStorageLocation storage;

    public WriteDoubleFieldNode(final int fieldIndex, final ObjectLayout layout,
        final AbstractWriteFieldNode next) {
      super(fieldIndex, layout, next);
      this.storage = (DoubleStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public double write(final SObject obj, final double value) {
      if (hasExpectedLayout(obj)) {
        storage.writeDouble(obj, value);
      } else {
        if (layout.layoutForSameClass(obj.getObjectLayout())) {
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache);
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (value instanceof Double) {
        write(obj, (double) value);
      } else {
        if (layout.layoutForSameClass(obj.getObjectLayout())) {
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache);
        } else {
          nextInCache.write(obj, (double) value);
        }
      }
      return value;
    }
  }

  public static final class WriteObjectFieldNode extends WriteSpecializedFieldNode {
    private final AbstractObjectStorageLocation storage;

    public WriteObjectFieldNode(final int fieldIndex, final ObjectLayout layout,
        final AbstractWriteFieldNode next) {
      super(fieldIndex, layout, next);
      this.storage = (AbstractObjectStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      if (hasExpectedLayout(obj)) {
        storage.write(obj, value);
      } else {
        if (layout.layoutForSameClass(obj.getObjectLayout())) {
          writeAndRespecialize(obj, value, "update outdated read node", nextInCache);
        } else {
          nextInCache.write(obj, value);
        }
      }
      return value;
    }
  }
}
