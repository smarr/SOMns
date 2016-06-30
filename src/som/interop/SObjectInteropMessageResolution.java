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
import som.vm.constants.Nil;
import som.vmobjects.SObjectWithClass;


@MessageResolution(receiverType = SObjectWithClass.class, language = SomLanguage.class)
public class SObjectInteropMessageResolution {

  @Resolve(message = "INVOKE")
  public abstract static class SObjectInvokeNode extends Node {
    @Child protected InteropDispatch dispatch = InteropDispatchNodeGen.create();
    @Child protected ToSomConversion convert = ToSomConversionNodeGen.create(null);

    protected Object access(final VirtualFrame frame, final SObjectWithClass rcvr,
        final String name, final Object[] args) {
      Object[] arguments = ValueConversion.convertToArgArray(convert, rcvr, args);

      Object result = dispatch.executeDispatch(frame, name, arguments);

      if (result == Nil.nilObject) {
        return null;
      } else {
        return result;
      }
    }
  }

  @Resolve(message = "WRITE")
  public abstract static class SObjectWriteNode extends Node {
    // TODO: this isn't ideal, we don't really need the InteropDispatch
    //       because it should be a precise lookup. But, for simplicity
    //       we reuse this for now

    @Child protected InteropDispatch dispatch = InteropDispatchNodeGen.create();
    @Child protected ToSomConversion convert = ToSomConversionNodeGen.create(null);

    protected Object access(final VirtualFrame frame, final SObjectWithClass rcvr,
        final String name, final Object value) {
      Object[] arguments = {rcvr, convert.executeEvaluated(value)};

      String setterName = name + ":";
      Object result = dispatch.executeDispatch(frame, setterName, arguments);

      if (result == Nil.nilObject) {
        return null;
      } else {
        return result;
      }
    }
  }

  @CanResolve
  public abstract static class CheckSClassWithClass extends Node {
    protected static boolean test(final TruffleObject receiver) {
      return receiver instanceof SObjectWithClass;
    }
  }
}
