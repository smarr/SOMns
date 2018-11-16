package tools.snapshot.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.interpreter.Types;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vmobjects.SObject;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;


@GenerateNodeFactory
public abstract class CachedSerializationNode extends AbstractSerializationNode {
  private final DispatchGuard             guard;
  private final AbstractSerializationNode cachedSerializer;

  public CachedSerializationNode(final Object o) {
    super(null);
    this.guard = DispatchGuard.create(o);
    this.cachedSerializer = Types.getClassOf(o).getSerializer();
  }

  @Specialization
  public void serialize(final Object o, final SnapshotBuffer sb) {
    try {
      if (guard.entryMatches(o)) {
        cachedSerializer.execute(o, sb);
      } else {
        Types.getClassOf(o).serialize(o, sb);
      }
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      SObject so = (SObject) o;
      if (!so.isLayoutCurrent()) {
        // we have to update the layout to avoid stackoverflow
        ObjectTransitionSafepoint.INSTANCE.transitionObject(so);
      }
      replace(CachedSerializationNodeFactory.create(o)).serialize(o, sb);
    }
  }

  @Override
  public Object deserialize(final DeserializationBuffer sb) {
    throw new UnsupportedOperationException("Use this node only for serialization");
  }
}
