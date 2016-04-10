package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.UnaryBasicOperation;
import tools.dym.Tags.OpArithmetic;


@GenerateNodeFactory
public abstract class NotMessageNode extends UnaryBasicOperation {
  public NotMessageNode(final boolean eagerlyWrapped, final SourceSection source) { super(eagerlyWrapped, source); }
  public NotMessageNode(final SourceSection source) { super(false, source); }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == OpArithmetic.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @Specialization
  public final boolean doNot(final VirtualFrame frame, final boolean receiver) {
    return !receiver;
  }
}
