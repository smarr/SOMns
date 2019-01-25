package tools.snapshot.nodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.interpreter.Types;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.vmobjects.SClass;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;
import tools.snapshot.nodes.ObjectSerializationNodes.SObjectSerializationNode;


@GenerateNodeFactory
public abstract class CachedSerializationNode extends AbstractSerializationNode {
  public static final int MAX_DEPTH = 8;

  protected final int depth;

  public CachedSerializationNode(final int depth) {
    super();
    this.depth = depth;
  }

  protected static DispatchGuard createDispatchGuard(final Object o) {
    return DispatchGuard.create(o);
  }

  protected static AbstractSerializationNode getSerializer(final Object o, final int depth) {
    SClass clazz = Types.getClassOf(o);
    NodeFactory<? extends AbstractSerializationNode> factory = clazz.getSerializerFactory();

    if (factory.getNodeClass() == SObjectSerializationNode.class) {
      return factory.createNode(clazz.getInstanceFactory(), depth + 1);
    }

    return factory.createNode();
  }

  protected static boolean execGuard(final Object o, final DispatchGuard guard,
      final Assumption objectLayoutIsLatest) {
    try {
      return guard.entryMatches(o);
    } catch (InvalidAssumptionException e) {
      return false; // Layout in guard is outdated
    }
  }

  @Specialization(guards = {"execGuard(o, guard, objectLayoutIsLatest)", "depth < MAX_DEPTH"},
      assumptions = "objectLayoutIsLatest")
  public void serialize(final Object o, final SnapshotBuffer sb,
      @Cached("createDispatchGuard(o)") final DispatchGuard guard,
      @Cached("guard.getAssumption()") final Assumption objectLayoutIsLatest,
      @Cached("getSerializer(o, depth)") final AbstractSerializationNode serializer) {
    serializer.execute(o, sb);
  }

  @Specialization
  public void fallback(final Object o, final SnapshotBuffer sb) {
    Types.getClassOf(o).serialize(o, sb);
  }

  @Override
  public Object deserialize(final DeserializationBuffer sb) {
    throw new UnsupportedOperationException("Use this node only for serialization");
  }
}
