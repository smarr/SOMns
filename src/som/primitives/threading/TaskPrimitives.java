package som.primitives.threading;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;

public final class TaskPrimitives {
  @GenerateNodeFactory
  @Primitive(primitive = "threadingSpawn:")
  public abstract static class SpawnPrim extends UnaryExpressionNode {
    public SpawnPrim(final boolean ew, final SourceSection s) { super(ew, s); }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "threadingSpawn:with:")
  public abstract static class SpawnWithPrim extends UnaryExpressionNode {
    public SpawnWithPrim(final boolean ew, final SourceSection s) { super(ew, s); }
  }
}
