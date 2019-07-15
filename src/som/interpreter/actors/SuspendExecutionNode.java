package som.interpreter.actors;

import com.oracle.truffle.api.debug.DebuggerTags.AlwaysHalt;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.Tag;

import som.interpreter.nodes.nary.UnaryExpressionNode;


public abstract class SuspendExecutionNode extends UnaryExpressionNode {
  private int skipFrames;

  SuspendExecutionNode(final int skipFrames) {
    this.skipFrames = skipFrames;
  }

  @Specialization
  public final Object doSAbstractObject(final Object receiver) {
    return receiver;
  }

  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == AlwaysHalt.class) {
      return true;
    }
    return super.hasTagIgnoringEagerness(tag);
  }

  public int getSkipFrames() {
    return skipFrames;
  }
}
