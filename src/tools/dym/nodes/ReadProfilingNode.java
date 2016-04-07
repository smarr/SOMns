package tools.dym.nodes;

import som.interpreter.Types;
import tools.dym.profiles.ReadValueProfile;

import com.oracle.truffle.api.frame.VirtualFrame;


public class ReadProfilingNode extends CountingNode<ReadValueProfile> {

  public ReadProfilingNode(final ReadValueProfile profile) {
    super(profile);
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    counter.profileValueType(Types.getClassOf(result));
  }
}
