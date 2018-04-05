package som.interop;

import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.Node;

import som.vm.constants.Nil;
import som.vmobjects.SAbstractObject;


@MessageResolution(receiverType = SAbstractObject.class)
public class SAbstractObjectInteropMessages {
  @Resolve(message = "IS_NULL")
  abstract static class NullCheckNode extends Node {

    public Object access(final SAbstractObject object) {
      return Nil.valueIsNil(object);
    }
  }
}
