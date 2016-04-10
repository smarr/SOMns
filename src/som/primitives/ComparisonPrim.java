package som.primitives;

import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.BinaryBasicOperation;
import tools.dym.Tags.OpComparison;


public abstract class ComparisonPrim extends BinaryBasicOperation {
  protected ComparisonPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == OpComparison.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }
}
