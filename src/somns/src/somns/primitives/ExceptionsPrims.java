package somns.primitives;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;

import bd.primitives.Primitive;
import somns.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import somns.interpreter.SomException;
import somns.interpreter.nodes.dispatch.BlockDispatchNode;
import somns.interpreter.nodes.nary.BinaryComplexOperation;
import somns.interpreter.nodes.nary.TernaryExpressionNode;
import somns.interpreter.nodes.nary.UnaryExpressionNode;
import somns.vm.VmSettings;
import somns.vmobjects.SAbstractObject;
import somns.vmobjects.SBlock;
import somns.vmobjects.SClass;
import somns.vmobjects.SInvokable;


public abstract class ExceptionsPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "exceptionDo:catch:onException:")
  public abstract static class ExceptionDoOnPrim extends TernaryExpressionNode {

    protected static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;

    protected static final IndirectCallNode indirect =
        Truffle.getRuntime().createIndirectCallNode();

    public static final DirectCallNode createCallNode(final SBlock block) {
      return Truffle.getRuntime().createDirectCallNode(
          block.getMethod().getCallTarget());
    }

    public static final boolean sameBlock(final SBlock block, final SInvokable method) {
      return block.getMethod() == method;
    }

    @Specialization(limit = "INLINE_CACHE_SIZE",
        guards = {"sameBlock(body, cachedBody)",
            "sameBlock(exceptionHandler, cachedExceptionMethod)"})
    public final Object doException(final SBlock body,
        final SClass exceptionClass, final SBlock exceptionHandler,
        @Cached("body.getMethod()") final SInvokable cachedBody,
        @Cached("createCallNode(body)") final DirectCallNode bodyCall,
        @Cached("exceptionHandler.getMethod()") final SInvokable cachedExceptionMethod,
        @Cached("createCallNode(exceptionHandler)") final DirectCallNode exceptionCall) {
      try {
        return bodyCall.call(new Object[] {body});
      } catch (SomException e) {
        if (e.getSomObject().getSOMClass().isKindOf(exceptionClass)) {
          return exceptionCall.call(new Object[] {exceptionHandler, e.getSomObject()});
        } else {
          throw e;
        }
      }
    }

    @Specialization(replaces = "doException")
    public final Object doExceptionUncached(final SBlock body,
        final SClass exceptionClass, final SBlock exceptionHandler) {
      try {
        return body.getMethod().invoke(indirect, new Object[] {body});
      } catch (SomException e) {
        if (e.getSomObject().getSOMClass().isKindOf(exceptionClass)) {
          return exceptionHandler.getMethod().invoke(indirect,
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
    @Specialization
    public final Object doSignal(final SAbstractObject exceptionObject) {
      throw new SomException(exceptionObject);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "exceptionDo:ensure:", selector = "ensure:",
      receiverType = SBlock.class)
  public abstract static class EnsurePrim extends BinaryComplexOperation {

    @Child protected BlockDispatchNode dispatchBody    = BlockDispatchNodeGen.create();
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    public final Object doException(final SBlock body, final SBlock ensureHandler) {
      try {
        return dispatchBody.executeDispatch(new Object[] {body});
      } finally {
        dispatchHandler.executeDispatch(new Object[] {ensureHandler});
      }
    }
  }
}
