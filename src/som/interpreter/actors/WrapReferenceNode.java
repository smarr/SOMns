package som.interpreter.actors;

import java.util.Map;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import som.VM;
import som.interpreter.actors.Actor.ExecutorRootNode;
import som.primitives.ObjectPrims.IsValue;
import som.primitives.ObjectPrimsFactory.IsValueFactory;
import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.replay.TraceRecord;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;


public abstract class WrapReferenceNode extends Node {
  @Child protected RecordOneEvent tracePromiseChaining;

  public WrapReferenceNode() {
    if (VmSettings.SENDER_SIDE_TRACING) {
      tracePromiseChaining = new RecordOneEvent(TraceRecord.PROMISE_CHAINED);
    }
  }

  public abstract Object execute(Object ref, Actor target, Actor owner);

  protected static final boolean notFarRef(final Object ref) {
    return !(ref instanceof SFarReference);
  }

  @Specialization(guards = {"target == owner", "notFarRef(ref)"})
  protected static Object inSameActor(final Object ref, final Actor target,
      final Actor owner) {
    return ref;
  }

  @Specialization(guards = "ref.getActor() == target")
  protected static Object farRefToTarget(final SFarReference ref, final Actor target,
      final Actor owner) {
    return ref.getValue();
  }

  @Specialization(guards = "ref.getActor() != target")
  protected static SFarReference farRefNotToTarget(final SFarReference ref, final Actor target,
      final Actor owner) {
    return ref;
  }

  @Specialization(guards = "promise.getOwner() == target")
  protected static SPromise promiseOwnedByTarget(final SPromise promise, final Actor target,
      final Actor owner) {
    return promise;
  }

  @Specialization(guards = "promise.getOwner() != target")
  protected SPromise promiseNotOwnedByTarget(final SPromise promise, final Actor target,
      final Actor owner) {
    return promise.getChainedPromiseFor(target, tracePromiseChaining);
  }

  protected static final boolean isNeitherFarRefNorPromise(final Object obj) {
    return !(obj instanceof SFarReference) && !(obj instanceof SPromise);
  }

  @Child protected IsValue isValue = IsValueFactory.create(null);

  protected final boolean isValue(final Object obj) {
    return isValue.executeBoolean(null, obj);
  }

  @Specialization(guards = {"isNeitherFarRefNorPromise(obj)", "isValue(obj)"})
  protected static Object isValueButNeitherFarRefNorPromiseObject(final Object obj,
      final Actor target, final Actor owner) {
    return obj;
  }

  protected static final boolean isTransferObj(final Object obj) {
    // TODO: optimize!
    return TransferObject.isTransferObject(obj);
  }

  @Specialization(
      guards = {"isNeitherFarRefNorPromise(obj)", "!isValue(obj)", "!isTransferObj(obj)"})
  protected static Object isNotValueNotFarRefNotPromiseObject(final Object obj,
      final Actor target, final Actor owner) {
    return new SFarReference(owner, obj);
  }

  @Specialization(guards = {"isTransferObj(obj)"})
  protected static Object isTransferObject(final SObject obj, final Actor target,
      final Actor owner) {
    return TransferObject.transfer(obj, owner, target, null);
  }

  @Specialization
  protected static Object isTransferArray(final STransferArray obj, final Actor target,
      final Actor owner) {
    return TransferObject.transfer(obj, owner, target, null);
  }

  public static final Object wrapForUse(final Actor target, final Object o, final Actor owner,
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    VM.thisMethodNeedsToBeOptimized("This should probably be optimized");

    // inSameActor
    if (target == owner) {
      return o;
    }

    if (o instanceof SFarReference) {
      SFarReference ref = (SFarReference) o;
      if (ref.getActor() == target) {
        // farRefToTarget
        return ref.getValue();
      } else {
        // farRefNotToTarget
        return ref;
      }
    }

    if (o instanceof SPromise) {
      // promises cannot just be wrapped in far references, instead, other actors
      // should get a new promise that is going to be resolved once the original
      // promise gets resolved

      SPromise orgProm = (SPromise) o;
      // assert orgProm.getOwner() == owner; this can be another actor, which initialized a
      // scheduled eventual send by resolving a promise, that's the promise pipelining...
      if (orgProm.getOwner() == target) {
        // promiseOwnedByTarget
        return orgProm;
      } else {
        // promiseNotOwnedByTarget
        return orgProm.getChainedPromiseFor(target,
            ((ExecutorRootNode) Actor.executorRoot.getRootNode()).recordPromiseChaining);
      }
    }

    if (IsValue.isObjectValue(o)) {
      // isValueButNeitherFarRefNorPromiseObject
      return o;
    }

    // Corresponds to TransferObject.isTransferObject()
    if ((o instanceof SObject && ((SObject) o).getSOMClass().isTransferObject())) {
      return TransferObject.transfer((SObject) o, owner, target, transferedObjects);
    } else if (o instanceof STransferArray) {
      return TransferObject.transfer((STransferArray) o, owner, target, transferedObjects);
    } else if (o instanceof SObjectWithoutFields
        && ((SObjectWithoutFields) o).getSOMClass().isTransferObject()) {
      return TransferObject.transfer((SObjectWithoutFields) o, owner, target,
          transferedObjects);
    } else {
      return new SFarReference(owner, o);
    }
  }
}
