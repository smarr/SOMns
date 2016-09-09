package som.primitives.arrays;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SMutableArray;


@GenerateNodeFactory
@Primitive(selector = "copy", receiverType = SArray.class, disabled = true)
public abstract class CopyPrim extends UnaryExpressionNode {
  public CopyPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  private final ValueProfile storageType = ValueProfile.createClassProfile();

  @Specialization(guards = "receiver.isEmptyType()")
  public final SMutableArray doEmptyArray(final SMutableArray receiver) {
    assert !receiver.getSOMClass().isTransferObject() : "Not yet supported, need to instantiate another class";
    return new SMutableArray(receiver.getEmptyStorage(storageType), receiver.getSOMClass());
  }

  @Specialization(guards = "receiver.isPartiallyEmptyType()")
  public final SMutableArray doPartiallyEmptyArray(final SMutableArray receiver) {
    assert !receiver.getSOMClass().isTransferObject() : "Not yet supported, need to instantiate another class";
    return new SMutableArray(receiver.getPartiallyEmptyStorage(storageType).copy(), receiver.getSOMClass());
  }

  @Specialization(guards = "receiver.isObjectType()")
  public final SMutableArray doObjectArray(final SMutableArray receiver) {
    assert !receiver.getSOMClass().isTransferObject() : "Not yet supported, need to instantiate another class";
    return new SMutableArray(receiver.getObjectStorage(storageType).clone(), receiver.getSOMClass());
  }

  @Specialization(guards = "receiver.isLongType()")
  public final SMutableArray doLongArray(final SMutableArray receiver) {
    assert !receiver.getSOMClass().isTransferObject() : "Not yet supported, need to instantiate another class";
    return new SMutableArray(receiver.getLongStorage(storageType).clone(), receiver.getSOMClass());
  }

  @Specialization(guards = "receiver.isDoubleType()")
  public final SMutableArray doDoubleArray(final SMutableArray receiver) {
    assert !receiver.getSOMClass().isTransferObject() : "Not yet supported, need to instantiate another class";
    return new SMutableArray(receiver.getDoubleStorage(storageType).clone(), receiver.getSOMClass());
  }

  @Specialization(guards = "receiver.isBooleanType()")
  public final SMutableArray doBooleanArray(final SMutableArray receiver) {
    assert !receiver.getSOMClass().isTransferObject() : "Not yet supported, need to instantiate another class";
    return new SMutableArray(receiver.getBooleanStorage(storageType).clone(), receiver.getSOMClass());
  }
}
