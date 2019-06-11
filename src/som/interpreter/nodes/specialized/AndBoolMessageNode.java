package som.interpreter.nodes.specialized;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Tag;

import bd.tools.nodes.Operation;
import som.interpreter.nodes.nary.BinaryBasicOperation;
import tools.dym.Tags.OpComparison;


@GenerateNodeFactory
public abstract class AndBoolMessageNode extends BinaryBasicOperation
    implements Operation {
  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == OpComparison.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  @Specialization
  public final boolean doAnd(final VirtualFrame frame, final boolean receiver,
      final boolean argument) {
    return receiver && argument;
  }

  @Override
  public String getOperation() {
    return "&&";
  }

  @Override
  public int getNumArguments() {
    return 2;
  }
}
