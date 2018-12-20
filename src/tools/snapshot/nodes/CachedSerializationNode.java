package tools.snapshot.nodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
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

  public CachedSerializationNode() {
    super();
  }

  protected static DispatchGuard createDispatchGuard(final Object o) {
    return DispatchGuard.create(o);
  }

  protected static AbstractSerializationNode getSerializer(final Object o) {
    // TODO: this is not yet creating a correct node. We have only a single node for all ASTs,
    // and we also need to remove the dependency on the class
    return Types.getClassOf(o).getSerializer();
  }

  protected static boolean execGuard(final Object o, final DispatchGuard guard,
      final Assumption objectLayoutIsLatest) {
    try {
      return guard.entryMatches(o);
    } catch (InvalidAssumptionException e) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      SObject so = (SObject) o;
      if (!so.isLayoutCurrent()) {
        // we have to update the layout to avoid stackoverflow
        ObjectTransitionSafepoint.INSTANCE.transitionObject(so);
      }
      return false;
    }
  }

  @Specialization(guards = "execGuard(o, guard, objectLayoutIsLatest)",
      assumptions = "objectLayoutIsLatest")
  public void serialize(final Object o, final SnapshotBuffer sb,
      @Cached("createDispatchGuard(o)") final DispatchGuard guard,
      @Cached("guard.getAssumption()") final Assumption objectLayoutIsLatest,
      @Cached("getSerializer(o)") final AbstractSerializationNode serializer) {
    serializer.execute(o, sb);
  }

  @Override
  public Object deserialize(final DeserializationBuffer sb) {
    throw new UnsupportedOperationException("Use this node only for serialization");
  }
}
