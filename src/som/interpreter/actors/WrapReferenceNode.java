package som.interpreter.actors;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import som.primitives.ObjectPrims.IsValue;
import som.primitives.ObjectPrimsFactory.IsValueFactory;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SObject;


public abstract class WrapReferenceNode extends Node {

  public abstract Object execute(Object ref, Actor target, Actor owner);

  @Specialization(guards = "target == owner")
  public Object inSameActor(final Object ref, final Actor target, final Actor owner) {
    return ref;
  }

  @Specialization(guards = "ref.getActor() == target")
  public Object farRefToTarget(final SFarReference ref, final Actor target,
      final Actor owner) {
    return ref.getValue();
  }

  @Specialization(guards = "ref.getActor() != target")
  public SFarReference farRefNotToTarget(final SFarReference ref, final Actor target,
      final Actor owner) {
    return ref;
  }

  @Specialization(guards = "promise.getOwner() == target")
  public SPromise promiseOwnedByTarget(final SPromise promise, final Actor target,
      final Actor owner) {
    return promise;
  }

  @Specialization(guards = "promise.getOwner() != target")
  public SPromise promiseNotOwnedByTarget(final SPromise promise, final Actor target,
      final Actor owner) {
    return promise.getChainedPromiseFor(target);
  }

  protected static final boolean isNeitherFarRefNorPromise(final Object obj) {
    return !(obj instanceof SFarReference) && !(obj instanceof SPromise);
  }

  @Child protected IsValue isValue = IsValueFactory.create(null);

  protected final boolean isValue(final Object obj) {
    return isValue.executeEvaluated(obj);
  }

  @Specialization(guards = {"isNeitherFarRefNorPromise(obj)", "isValue(obj)"})
  public Object isValueObject(final Object obj, final Actor target, final Actor owner) {
    return obj;
  }

  protected final boolean isTransferObj(final Object obj) {
    // TODO: optimize!
    return TransferObject.isTransferObject(obj);
  }

  @Specialization(
      guards = {"isNeitherFarRefNorPromise(obj)", "!isValue(obj)", "!isTransferObj(obj)"})
  public Object isNotValueObject(final Object obj, final Actor target, final Actor owner) {
    return new SFarReference(owner, obj);
  }

  @Specialization(guards = {"isTransferObj(obj)"})
  public Object isTransferObject(final SObject obj, final Actor target, final Actor owner) {
    return TransferObject.transfer(obj, owner, target, null);
  }

  @Specialization
  public Object isTransferArray(final STransferArray obj, final Actor target,
      final Actor owner) {
    return TransferObject.transfer(obj, owner, target, null);
  }
}
