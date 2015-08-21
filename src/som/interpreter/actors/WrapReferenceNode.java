package som.interpreter.actors;

import som.primitives.ObjectPrims.IsValue;
import som.primitives.ObjectPrimsFactory.IsValueFactory;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;


public abstract class WrapReferenceNode extends Node {

  public abstract Object execute(Object ref, Actor target, Actor owner);

  @Specialization(guards = "target == owner")
  public Object inSameActor(final Object ref, final Actor target, final Actor owner) {
    return ref;
  }

  @Specialization(guards = "ref.getActor() == target")
  public Object farRefToTarget(final SFarReference ref, final Actor target, final Actor owner) {
    return ref.getValue();
  }

  @Specialization(guards = "ref.getActor() != target")
  public SFarReference farRefNotToTarget(final SFarReference ref, final Actor target, final Actor owner) {
    return ref;
  }

  @Specialization(guards = "promise.getOwner() == target")
  public SPromise promiseOwnedByTarget(final SPromise promise, final Actor target, final Actor owner) {
    return promise;
  }

  @Specialization(guards = "promise.getOwner() != target")
  public SPromise promiseNotOwnedByTarget(final SPromise promise, final Actor target, final Actor owner) {
    SPromise remote = SPromise.createPromise(target);
    synchronized (promise) {
      if (promise.isSomehowResolved()) {
        promise.copyValueToRemotePromise(remote);
      } else {
        promise.addChainedPromise(remote);
      }
      return remote;
    }
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

  @Specialization(guards = {"isNeitherFarRefNorPromise(obj)", "!isValue(obj)"})
  public Object isNotValueObject(final Object obj, final Actor target, final Actor owner) {
    return new SFarReference(owner, obj);
  }
}
