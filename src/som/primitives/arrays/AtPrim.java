package som.primitives.arrays;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.BinaryBasicOperation;
import som.primitives.Primitive;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import tools.dym.Tags.ArrayRead;


@GenerateNodeFactory
@Primitive("array:at:")
public abstract class AtPrim extends BinaryBasicOperation {
  private final ValueProfile storageType = ValueProfile.createClassProfile();

  protected AtPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected AtPrim(final SourceSection source) { super(false, source); }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == ArrayRead.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  @Specialization(guards = "receiver.isEmptyType()")
  public final Object doEmptySArray(final SArray receiver, final long idx) {
    assert idx > 0;
    assert idx <= receiver.getEmptyStorage(storageType);
    return Nil.nilObject;
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final Object doPartiallyEmptySArray(final SArray receiver, final long idx) {
    return receiver.getPartiallyEmptyStorage(storageType).get(idx - 1);
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final Object doObjectSArray(final SArray receiver, final long idx) {
    return receiver.getObjectStorage(storageType)[(int) idx - 1];
  }

  @Specialization(guards = "receiver.isLongType()")
  public final long doLongSArray(final SArray receiver, final long idx) {
    return receiver.getLongStorage(storageType)[(int) idx - 1];
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final double doDoubleSArray(final SArray receiver, final long idx) {
    return receiver.getDoubleStorage(storageType)[(int) idx - 1];
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final boolean doBooleanSArray(final SArray receiver, final long idx) {
    return receiver.getBooleanStorage(storageType)[(int) idx - 1];
  }
}
