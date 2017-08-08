package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;

import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.vmobjects.SArray;
import som.vmobjects.SSymbol;
import tools.dym.Tags.OpLength;


@GenerateNodeFactory
@Primitive(primitive = "arraySize:", selector = "size", receiverType = SArray.class,
    inParser = false)
@Primitive(primitive = "stringLength:", selector = "length", receiverType = String.class,
    inParser = false)
public abstract class SizeAndLengthPrim extends UnaryBasicOperation {
  private final ValueProfile storageType = ValueProfile.createClassProfile();

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == OpLength.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @Specialization(guards = "receiver.isEmptyType()")
  public final long doEmptySArray(final SArray receiver) {
    return receiver.getEmptyStorage(storageType);
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final long doPartialEmptySArray(final SArray receiver) {
    return receiver.getPartiallyEmptyStorage(storageType).getLength();
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final long doObjectSArray(final SArray receiver) {
    return receiver.getObjectStorage(storageType).length;
  }

  @Specialization(guards = "receiver.isLongType()")
  public final long doLongSArray(final SArray receiver) {
    return receiver.getLongStorage(storageType).length;
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final long doDoubleSArray(final SArray receiver) {
    return receiver.getDoubleStorage(storageType).length;
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final long doBooleanSArray(final SArray receiver) {
    return receiver.getBooleanStorage(storageType).length;
  }

  public abstract long executeEvaluated(SArray receiver);

  @Specialization
  public final long doString(final String receiver) {
    return receiver.length();
  }

  @Specialization
  public final long doSSymbol(final SSymbol receiver) {
    return receiver.getString().length();
  }
}
