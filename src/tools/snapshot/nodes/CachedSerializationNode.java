package tools.snapshot.nodes;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import som.interpreter.Types;
import som.interpreter.nodes.dispatch.DispatchGuard;
import tools.snapshot.SnapshotBuffer;


public class CachedSerializationNode extends AbstractSerializationNode {
  private final DispatchGuard             guard;
  private final AbstractSerializationNode cachedSerializer;

  public CachedSerializationNode(final Object o) {
    super();
    this.guard = DispatchGuard.create(o);
    this.cachedSerializer = Types.getClassOf(o).getSerializer();
  }

  @Override
  public void serialize(final Object o, final SnapshotBuffer sb) {
    try {
      if (guard.entryMatches(o)) {
        cachedSerializer.serialize(o, sb);
      } else {
        Types.getClassOf(o).getSerializer().serialize(o, sb);
      }
    } catch (InvalidAssumptionException e) {
      // checked layout is outdated
      CompilerDirectives.transferToInterpreterAndInvalidate();
      replace(new CachedSerializationNode(o)).serialize(o, sb);
    }
  }

  @Override
  public Object deserialize(final ByteBuffer sb) {
    throw new UnsupportedOperationException("Use this node only for serialization");
  }
}
