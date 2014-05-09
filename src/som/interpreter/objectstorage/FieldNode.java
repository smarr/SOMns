package som.interpreter.objectstorage;

import som.interpreter.TruffleCompiler;
import som.interpreter.TypesGen;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.PreevaluatedExpression;
import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.interpreter.objectstorage.StorageLocation.AbstractObjectStorageLocation;
import som.interpreter.objectstorage.StorageLocation.DoubleStorageLocation;
import som.interpreter.objectstorage.StorageLocation.LongStorageLocation;
import som.vm.Universe;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;


public abstract class FieldNode extends ExpressionNode {
  protected static final int INLINE_CACHE_SIZE = 6;

  protected final int fieldIndex;

  @Child protected ExpressionNode self;

  public FieldNode(final ExpressionNode self, final int fieldIndex) {
    this.self       = self;
    this.fieldIndex = fieldIndex;
  }

  public final boolean accessesLocalSelf() {
    if (self instanceof UninitializedVariableReadNode) {
      UninitializedVariableReadNode selfRead =
          (UninitializedVariableReadNode) self;
      return selfRead.accessesSelf() && !selfRead.accessesOuterContext();
    }
    return false;
  }

  public final int getFieldIndex() {
    return fieldIndex;
  }

  public abstract static class AbstractReadFieldNode extends FieldNode
      implements PreevaluatedExpression {
    public AbstractReadFieldNode(final ExpressionNode self, final int fieldIndex) {
      super(self, fieldIndex);
    }

    public boolean isSet(final SObject obj) {
      return true;
    }

    public final Object executeEvaluated(final SObject obj) {
      return read(obj);
    }

    @Override
    public final Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return executeEvaluated((SObject) arguments[0]);
    }

    @Override
    public final long executeLong(final VirtualFrame frame) throws UnexpectedResultException {
      SObject obj = self.executeSObject(frame);
      return readLong(obj);
    }

    @Override
    public final double executeDouble(final VirtualFrame frame) throws UnexpectedResultException {
      SObject obj = self.executeSObject(frame);
      return readDouble(obj);
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      SObject obj;
      try {
        obj = self.executeSObject(frame);
      } catch (UnexpectedResultException e) {
        throw new RuntimeException("This should never happen by construction");
      }
      return executeEvaluated(obj);
    }

    @Override
    public void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }

    protected abstract Object read(SObject obj);

    protected long readLong(final SObject obj) throws UnexpectedResultException {
      return TypesGen.TYPES.expectLong(read(obj));
    }

    protected double readDouble(final SObject obj) throws UnexpectedResultException {
      return TypesGen.TYPES.expectDouble(read(obj));
    }

    protected Object specializeAndRead(final SObject obj, final String reason, final AbstractReadFieldNode next) {
      return specialize(obj, reason, next).read(obj);
    }

    protected AbstractReadFieldNode specialize(final SObject obj,
        final String reason, final AbstractReadFieldNode next) {
      obj.updateLayoutToMatchClass();

      final ObjectLayout    layout   = obj.getObjectLayout();
      final StorageLocation location = layout.getStorageLocation(fieldIndex);

      AbstractReadFieldNode newNode = location.getReadNode(this.self, fieldIndex, layout, next);
      return replace(newNode, reason);
    }
  }

  public static final class UninitializedReadFieldNode extends AbstractReadFieldNode {

    public UninitializedReadFieldNode(final ExpressionNode self, final int fieldIndex) {
      super(self, fieldIndex);
    }

    @Override
    public Object read(final SObject obj) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return specializeAndRead(obj, "uninitalized node", new UninitializedReadFieldNode(self, fieldIndex));
    }
  }

  public abstract static class ReadSpecializedFieldNode extends AbstractReadFieldNode {
    protected final ObjectLayout layout;
    @Child private AbstractReadFieldNode nextInCache;

    public ReadSpecializedFieldNode(final ExpressionNode self,
        final int fieldIndex, final ObjectLayout layout,
        final AbstractReadFieldNode next) {
      super(self, fieldIndex);
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
    private final SObject nilObject;

    public ReadUnwrittenFieldNode(final ExpressionNode self,
        final int fieldIndex, final ObjectLayout layout,
        final AbstractReadFieldNode next) {
      super(self, fieldIndex, layout, next);
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
        return respecializedNodeOrNext(obj).read(obj);
      }
    }
  }

  public static final class ReadLongFieldNode extends ReadSpecializedFieldNode {
    private final LongStorageLocation storage;

    public ReadLongFieldNode(final ExpressionNode self, final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(self, fieldIndex, layout, next);
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

    public ReadDoubleFieldNode(final ExpressionNode self, final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(self, fieldIndex, layout, next);
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
        return readLong(obj);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }
  }

  public static final class ReadObjectFieldNode extends ReadSpecializedFieldNode {
    private final AbstractObjectStorageLocation storage;

    public ReadObjectFieldNode(final ExpressionNode self, final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      super(self, fieldIndex, layout, next);
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

  public abstract static class AbstractWriteFieldNode extends FieldNode
      implements PreevaluatedExpression {
    @Child protected ExpressionNode value;

    public AbstractWriteFieldNode(final ExpressionNode self,
        final ExpressionNode value, final int fieldIndex) {
      super(self, fieldIndex);
      this.value = value;
    }

    public final Object executeEvaluated(final VirtualFrame frame, final SObject self, final Object value) {
      return write(self, value);
    }

    @Override
    public final Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return executeEvaluated(frame, (SObject) arguments[0], arguments[1]);
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

    protected void writeAndRespecialize(final SObject obj, final Object value,
        final String reason, final AbstractWriteFieldNode next) {
      TruffleCompiler.transferToInterpreterAndInvalidate(reason);

      obj.setField(fieldIndex, value);

      final ObjectLayout layout = obj.getObjectLayout();
      final StorageLocation location = layout.getStorageLocation(fieldIndex);
      AbstractWriteFieldNode newNode = location.getWriteNode(this.self, this.value, fieldIndex, layout, next);
      replace(newNode, reason);
    }

    @Override
    public long executeLong(final VirtualFrame frame)
        throws UnexpectedResultException {
      SObject obj = executeSelf(frame);
      long val = value.executeLong(frame);
      return write(obj, val);
    }

    @Override
    public double executeDouble(final VirtualFrame frame)
        throws UnexpectedResultException {
      SObject obj = executeSelf(frame);
      double val  = value.executeDouble(frame);
      return write(obj, val);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      SObject obj = executeSelf(frame);
      Object  val = value.executeGeneric(frame);
      return executeEvaluated(frame, obj, val);
    }

    private SObject executeSelf(final VirtualFrame frame) {
      SObject obj;
      try {
        obj = self.executeSObject(frame);
      } catch (UnexpectedResultException e) {
        throw new RuntimeException("This should never happen by construction");
      }
      return obj;
    }

    @Override
    public void executeVoid(final VirtualFrame frame) {
      executeGeneric(frame);
    }
  }

  public static final class UninitializedWriteFieldNode extends AbstractWriteFieldNode {
    public UninitializedWriteFieldNode(final ExpressionNode self,
        final ExpressionNode value, final int fieldIndex) {
      super(self, value, fieldIndex);
    }

    @Override
    public Object write(final SObject obj, final Object value) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      writeAndRespecialize(obj, value, "initialize write field node",
          new UninitializedWriteFieldNode(this.self, this.value, fieldIndex));
      return value;
    }
  }

  private abstract static class WriteSpecializedFieldNode extends AbstractWriteFieldNode {

    protected final ObjectLayout layout;
    @Child protected AbstractWriteFieldNode nextInCache;

    public WriteSpecializedFieldNode(final ExpressionNode self,
        final ExpressionNode value, final int fieldIndex,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      super(self, value, fieldIndex);
      this.layout = layout;
      nextInCache = next;
    }

    protected final boolean hasExpectedLayout(final SObject obj) {
      return layout == obj.getObjectLayout();
    }
  }

  public static final class WriteLongFieldNode extends WriteSpecializedFieldNode {
    private final LongStorageLocation storage;

    public WriteLongFieldNode(final ExpressionNode self,
        final ExpressionNode value, final int fieldIndex,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      super(self, value, fieldIndex, layout, next);
      this.storage = (LongStorageLocation) layout.getStorageLocation(fieldIndex);
    }

    @Override
    public long write(final SObject obj, final long value) {
      if (hasExpectedLayout(obj)) {
        storage.writeLong(obj, value);
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

    public WriteDoubleFieldNode(final ExpressionNode self,
        final ExpressionNode value, final int fieldIndex, final ObjectLayout layout,
        final AbstractWriteFieldNode next) {
      super(self, value, fieldIndex, layout, next);
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

    public WriteObjectFieldNode(final ExpressionNode self,
        final ExpressionNode value, final int fieldIndex, final ObjectLayout layout,
        final AbstractWriteFieldNode next) {
      super(self, value, fieldIndex, layout, next);
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
