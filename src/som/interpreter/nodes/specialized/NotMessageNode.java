package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import bd.primitives.Primitive;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import tools.dym.Tags.OpArithmetic;


@GenerateNodeFactory
@Primitive(selector = "not", receiverType = Boolean.class)
public abstract class NotMessageNode extends UnaryBasicOperation {
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
