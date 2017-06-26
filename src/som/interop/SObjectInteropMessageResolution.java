package som.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

import som.VM;
import som.interop.ValueConversion.ToSomConversion;
import som.interop.ValueConversionFactory.ToSomConversionNodeGen;
import som.interpreter.SomLanguage;
import som.vm.constants.Nil;
import som.vmobjects.SObjectWithClass;


@MessageResolution(receiverType = SObjectWithClass.class)
public class SObjectInteropMessageResolution {

  @Resolve(message = "INVOKE")
  public abstract static class SObjectInvokeNode extends Node {

    @Child protected ToSomConversion convert = ToSomConversionNodeGen.create(null);
    @Child protected InteropDispatch dispatch;

    @TruffleBoundary
    protected final void ensureDispatch(final SObjectWithClass rcvr) {
      if (dispatch == null) {
        // TODO: this is a bad hack. Ideally, we can pass the VM in somewhat more direct and robustly
        VM vm = rcvr.getSOMClass().getMethods()[0].getInvokable().getRootNode().getLanguage(SomLanguage.class).getVM();
        dispatch = InteropDispatchNodeGen.create(vm);
      }
    }

    protected Object access(final VirtualFrame frame, final SObjectWithClass rcvr,
        final String name, final Object[] args) {
      ensureDispatch(rcvr);

      Object[] arguments = ValueConversion.convertToArgArray(convert, rcvr, args);

      Object result = dispatch.executeDispatch(frame, name, arguments);
      return result;
    }
  }

  @Resolve(message = "WRITE")
  public abstract static class SObjectWriteNode extends Node {
    // TODO: this isn't ideal, we don't really need the InteropDispatch
    //       because it should be a precise lookup. But, for simplicity
    //       we reuse this for now

    @Child protected ToSomConversion convert = ToSomConversionNodeGen.create(null);
    @Child protected InteropDispatch dispatch;

    @TruffleBoundary
    protected final void ensureDispatch(final SObjectWithClass rcvr) {
      if (dispatch == null) {
        // TODO: this is a bad hack. Ideally, we can pass the VM in somewhat more direct and robustly
        VM vm = rcvr.getSOMClass().getMethods()[0].getInvokable().getRootNode().getLanguage(SomLanguage.class).getVM();
        dispatch = InteropDispatchNodeGen.create(vm);
      }
    }

    protected Object access(final VirtualFrame frame, final SObjectWithClass rcvr,
        final String name, final Object value) {
      ensureDispatch(rcvr);

      Object[] arguments = {rcvr, convert.executeEvaluated(value)};

      String setterName = name + ":";
      Object result = dispatch.executeDispatch(frame, setterName, arguments);
      return result;
    }
  }

  @Resolve(message = "IS_NULL")
  public abstract static class SObjectIsNilNode extends Node {
    public Object access(final SObjectWithClass rcvr) {
      return rcvr == Nil.nilObject;
    }
  }

  @CanResolve
  public abstract static class CheckSClassWithClass extends Node {
    protected static boolean test(final TruffleObject receiver) {
      return receiver instanceof SObjectWithClass;
    }
  }
}
