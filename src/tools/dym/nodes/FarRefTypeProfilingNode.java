package tools.dym.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;

import som.interpreter.actors.SFarReference;
import som.vm.NotYetImplementedException;
import tools.dym.profiles.ActorCreationProfile;


public class FarRefTypeProfilingNode extends CountingNode<ActorCreationProfile> {

  @Child protected TypeProfileNode type;

  public FarRefTypeProfilingNode(final ActorCreationProfile counter) {
    super(counter);
    type = TypeProfileNodeGen.create(counter);
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    if (result instanceof SFarReference) {
      SFarReference obj = (SFarReference) result;
      type.executeProfiling(obj.getValue());
    } else {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      throw new NotYetImplementedException();
    }
  }
}
