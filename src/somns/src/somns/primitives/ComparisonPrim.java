package somns.primitives;

import com.oracle.truffle.api.instrumentation.Tag;

import somns.interpreter.nodes.nary.BinaryBasicOperation;
import tools.dym.Tags.OpComparison;


public abstract class ComparisonPrim extends BinaryBasicOperation {
  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == OpComparison.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }
}
