package som.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

import som.interop.ValueConversion.ToSomConversion;
import som.interop.ValueConversionFactory.ToSomConversionNodeGen;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;


@MessageResolution(receiverType = SBlock.class, language = SomLanguage.class)
public class SBlockInteropMessageResolution {

  @Resolve(message = "EXECUTE")
  public abstract static class SBlockExecuteNode extends Node {

    @Child protected BlockDispatchNode block = BlockDispatchNodeGen.create();
    @Child protected ToSomConversion convert = ToSomConversionNodeGen.create(null);

    public Object access(final VirtualFrame frame, final SBlock rcvr,
        final Object[] args) {
      ObjectTransitionSafepoint.INSTANCE.register();

      try {
        Object[] arguments = ValueConversion.convertToArgArray(convert, rcvr, args);
        Object result = block.executeDispatch(frame, arguments);

        if (result == Nil.nilObject) {
          return null;
        } else {
          return result;
        }
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }
    }
  }

  @CanResolve
  public abstract static class CheckSBlock extends Node {
    protected static boolean test(final TruffleObject receiver) {
      return receiver instanceof SBlock;
    }
  }
}
