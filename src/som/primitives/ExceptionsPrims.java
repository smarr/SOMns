package som.primitives;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.VmSettings;
import som.interpreter.SomException;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;


public abstract class ExceptionsPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "exceptionDo:catch:onException:")
  public abstract static class ExceptionDoOnPrim extends TernaryExpressionNode {

    protected static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;
    protected static final IndirectCallNode indirect = Truffle.getRuntime().createIndirectCallNode();

    public static final DirectCallNode createCallNode(final SBlock block) {
      return Truffle.getRuntime().createDirectCallNode(
          block.getMethod().getCallTarget());
    }

    public ExceptionDoOnPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    public static final boolean sameBlock(final SBlock block, final SInvokable method) {
      return block.getMethod() == method;
    }

    @Specialization(limit = "INLINE_CACHE_SIZE",
        guards = {"sameBlock(body, cachedBody)",
                  "sameBlock(exceptionHandler, cachedExceptionMethod)"})
    public final Object doException(final VirtualFrame frame, final SBlock body,
        final SClass exceptionClass, final SBlock exceptionHandler,
        @Cached("body.getMethod()") final SInvokable cachedBody,
        @Cached("createCallNode(body)") final DirectCallNode bodyCall,
        @Cached("exceptionHandler.getMethod()") final SInvokable cachedExceptionMethod,
        @Cached("createCallNode(exceptionHandler)") final DirectCallNode exceptionCall) {
      try {
        return bodyCall.call(frame, new Object[] {body});
      } catch (SomException e) {
        if (e.getSomObject().getSOMClass().isKindOf(exceptionClass)) {
          return exceptionCall.call(frame,
              new Object[] {exceptionHandler, e.getSomObject()});
        } else {
          throw e;
        }
      }
    }

    @Specialization(contains = "doException")
    public final Object doExceptionUncached(final VirtualFrame frame, final SBlock body,
        final SClass exceptionClass, final SBlock exceptionHandler) {
      try {
        return body.getMethod().invoke(indirect, frame, new Object[] {body});
      } catch (SomException e) {
        if (e.getSomObject().getSOMClass().isKindOf(exceptionClass)) {
          return exceptionHandler.getMethod().invoke(indirect, frame,
              new Object[] {exceptionHandler, e.getSomObject()});
        } else {
          throw e;
        }
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "signalException:")
  public abstract static class SignalPrim extends UnaryExpressionNode {
    public SignalPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final Object doSignal(final SAbstractObject exceptionObject) {
      throw new SomException(exceptionObject);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "exceptionDo:ensure:")
  public abstract static class EnsurePrim extends BinaryComplexOperation {

    @Child protected BlockDispatchNode dispatchBody    = BlockDispatchNodeGen.create();
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    protected EnsurePrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }

    @Specialization
    public final Object doException(final VirtualFrame frame, final SBlock body,
        final SBlock ensureHandler) {
      try {
        return dispatchBody.executeDispatch(frame, new Object[] {body});
      } finally {
        dispatchHandler.executeDispatch(frame,
            new Object[] {ensureHandler});
      }
    }
  }
}
