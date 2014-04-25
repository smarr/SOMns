package som.interpreter.nodes;

import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;


public abstract class FieldAccessor extends Node {

  public static FieldAccessor create(final int fieldIndex) {
    if (fieldIndex < SObject.NUM_DIRECT_FIELDS) {
      return new DirectStoreAccessor(fieldIndex);
    } else {
      return new ArrayStoreAccessor(fieldIndex);
    }
  }

  public abstract Object read(final SObject self);
  public abstract void   write(final SObject self, final Object value);


  private static final class ArrayStoreAccessor extends FieldAccessor {
    private final int extensionFieldIndex;

    ArrayStoreAccessor(final int fieldIndex) {
      assert fieldIndex >= SObject.NUM_DIRECT_FIELDS;
      this.extensionFieldIndex = fieldIndex - SObject.NUM_DIRECT_FIELDS;
    }

    @Override
    public Object read(final SObject self) {
      return self.getExtensionField(extensionFieldIndex);
    }

    @Override
    public void write(final SObject self, final Object value) {
      self.setExtensionField(extensionFieldIndex, value);
    }
  }

  private static final class DirectStoreAccessor extends FieldAccessor {
    private final long fieldOffset;
//    private final Object fieldIdentifierToken;

    DirectStoreAccessor(final int fieldIndex) {
      assert fieldIndex < SObject.NUM_DIRECT_FIELDS;
      fieldOffset = SObject.FIRST_OFFSET + fieldIndex * SObject.FIELD_LENGTH;
//      fieldIdentifierToken = fieldIdentifierTokens[fieldIndex];
    }

    @Override
    public Object read(final SObject self) {
      //return CompilerDirectives.unsafeGetObject(self, fieldOffset, true, fieldIdentifierToken);
      return CompilerDirectives.unsafeGetObject(self, fieldOffset, true, null);
    }


    @Override
    public void write(final SObject self, final Object value) {
      // CompilerDirectives.unsafePutObject(self, fieldOffset, value, fieldIdentifierToken);
      CompilerDirectives.unsafePutObject(self, fieldOffset, value, null);
    }

// Disabled because of compilation correctness issues in Graal.
//    private static Object[] fieldIdentifierTokens;
//    // static initializer
//    {
//      fieldIdentifierTokens = new Object[SObject.NUM_DIRECT_FIELDS];
//      for (int i = 0; i < SObject.NUM_DIRECT_FIELDS; i++) {
//        fieldIdentifierTokens[i] = new Object();
//      }
//    }
  }
}
