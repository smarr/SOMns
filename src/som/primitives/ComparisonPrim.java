package som.primitives;

import som.interpreter.nodes.nary.BinaryBasicOperation;
import tools.dym.Tags.OpComparison;


public abstract class ComparisonPrim extends BinaryBasicOperation {
  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == OpComparison.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }
}
