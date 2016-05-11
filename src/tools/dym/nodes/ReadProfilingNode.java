package tools.dym.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.Types;
import tools.dym.profiles.ReadValueProfile;


public class ReadProfilingNode extends CountingNode<ReadValueProfile> {

  public ReadProfilingNode(final ReadValueProfile profile) {
    super(profile);
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    profileResult(result);
  }

  @TruffleBoundary
  private void profileResult(final Object result) {
    // TODO: we could potentially specialize the getClassOf
    counter.profileValueType(Types.getClassOf(result).getInstanceFactory());
  }
}
