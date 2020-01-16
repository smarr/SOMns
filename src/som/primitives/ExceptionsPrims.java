package som.primitives;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;

import bd.primitives.Primitive;
import som.interpreter.SomException;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import com.oracle.truffle.api.frame.VirtualFrame;
import som.interpreter.SArguments;
import tools.asyncstacktraces.ShadowStackEntryLoad;


public abstract class ExceptionsPrims {

  @GenerateNodeFactory
  @Primitive(primitive = "exceptionDo:catch:onException:")
  public abstract static class ExceptionDoOnPrim extends TernaryExpressionNode {

    protected static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;

    @Child
    protected ShadowStackEntryLoad shadowStackEntryLoadBody =
            ShadowStackEntryLoad.create();

    @Child
    protected ShadowStackEntryLoad shadowStackEntryLoadException =
            ShadowStackEntryLoad.create();

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
    public final Object doException(final VirtualFrame frame, final SBlock body,
                                    final SClass exceptionClass, final SBlock exceptionHandler,
                                    @Cached("body.getMethod()") final SInvokable cachedBody,
                                    @Cached("createCallNode(body)") final DirectCallNode bodyCall,
                                    @Cached("exceptionHandler.getMethod()") final SInvokable cachedExceptionMethod,
                                    @Cached("createCallNode(exceptionHandler)") final DirectCallNode exceptionCall) {
      try {
        Object[] args;
        if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
          args = new Object[]{body, null};
          SArguments.setShadowStackEntryWithCache(args, this, shadowStackEntryLoadBody, frame,
                  false);
        } else {
          args = new Object[]{body};
        }
        return bodyCall.call(args);
      } catch (SomException e) {
        if (e.getSomObject().getSOMClass().isKindOf(exceptionClass)) {
          Object[] args;
          if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
            args = new Object[]{exceptionHandler, e.getSomObject(), null};
            SArguments.setShadowStackEntryWithCache(args, this, shadowStackEntryLoadException,
                    frame,
                    false);
          } else {
            args = new Object[]{exceptionHandler, e.getSomObject()};
          }
          return exceptionCall.call(args);
        } else {
          throw e;
        }
      }
    }

    @Specialization(replaces = "doException")
    public final Object doExceptionUncached(final VirtualFrame frame, final SBlock body,
                                            final SClass exceptionClass, final SBlock exceptionHandler) {
      try {
        Object[] args;
        if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
          args = new Object[]{body, null};
          SArguments.setShadowStackEntryWithCache(args, this, shadowStackEntryLoadBody, frame,
                  false);
        } else {
          args = new Object[]{body};
        }
        return body.getMethod().invoke(indirect, args);
      } catch (SomException e) {
        if (e.getSomObject().getSOMClass().isKindOf(exceptionClass)) {
          Object[] args;
          if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
            args = new Object[]{exceptionHandler, e.getSomObject(), null};
            SArguments.setShadowStackEntryWithCache(args, this, shadowStackEntryLoadException,
                    frame,
                    false);
          } else {
            args = new Object[]{exceptionHandler, e.getSomObject()};
          }
          return exceptionHandler.getMethod().invoke(indirect, args);
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

    @Child
    protected BlockDispatchNode dispatchBody = BlockDispatchNodeGen.create();
    @Child
    protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Child
    protected ShadowStackEntryLoad shadowStackEntryLoadBody =
            ShadowStackEntryLoad.create();
    @Child
    protected ShadowStackEntryLoad shadowStackEntryLoadHandler =
            ShadowStackEntryLoad.create();

    @Specialization
    public final Object doException(final VirtualFrame frame, final SBlock body,
                                    final SBlock ensureHandler) {
      if (VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE) {
        // losing SSEntry info here again
        try {
          Object[] args = new Object[]{body, null};
          SArguments.setShadowStackEntryWithCache(args, this, shadowStackEntryLoadBody, frame,
                  false);
          return dispatchBody.executeDispatch(args);
        } finally {
          Object[] args = new Object[]{ensureHandler, null};
          SArguments.setShadowStackEntryWithCache(args, this, shadowStackEntryLoadHandler,
                  frame,
                  false);
          dispatchHandler.executeDispatch(args);
        }
      } else {
        try {
          return dispatchBody.executeDispatch(new Object[]{body});
        } finally {
          dispatchHandler.executeDispatch(new Object[]{ensureHandler});
        }
      }
    }
  }
}
