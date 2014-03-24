package som.interpreter.nodes;

import som.vmobjects.SObject;
import som.vmobjects.SObject.SObjectN;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;


public abstract class FieldAccessor extends Node {
  public abstract Object read(final SObject self);
  public abstract void   write(final SObject self, final Object value);

  public static final class UninitializedFieldAccessor extends FieldAccessor {
    private final int fieldIndex;

    public UninitializedFieldAccessor(final int fieldIndex) {
      this.fieldIndex = fieldIndex;
    }

    @Override
    public Object read(final SObject self) {
      return specialize(self).read(self);
    }

    @Override
    public void write(final SObject self, final Object value) {
      specialize(self).write(self, value);
    }

    private FieldAccessor specialize(final SObject self) {
      if (self instanceof SObjectN) {
        return replace(new ArrayStoreAccessor(fieldIndex));
      } else {
        return replace(new DirectStoreAccessor(fieldIndex));
      }
    }
  }

  private static final class ArrayStoreAccessor extends FieldAccessor {
    private final int fieldIndex;

    public ArrayStoreAccessor(final int fieldIndex) {
      this.fieldIndex = fieldIndex;
    }

    @Override
    public Object read(final SObject self) {
      return ((SObjectN) self).getField(fieldIndex);
    }

    @Override
    public void write(final SObject self, final Object value) {
      ((SObjectN) self).setField(fieldIndex, value);
    }
  }

  private static final class DirectStoreAccessor extends FieldAccessor {
    private final long fieldOffset;

    public DirectStoreAccessor(final int fieldIndex) {
      fieldOffset = SObject.FIRST_OFFSET + fieldIndex * SObject.FIELD_LENGTH;
    }

    @Override
    public Object read(final SObject self) {
      return CompilerDirectives.unsafeGetObject(self, fieldOffset, true, this);
    }


    @Override
    public void write(final SObject self, final Object value) {
      CompilerDirectives.unsafePutObject(self, fieldOffset, value, this);
    }
  }
}
