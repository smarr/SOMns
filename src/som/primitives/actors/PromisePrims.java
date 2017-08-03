package som.primitives.actors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.DirectCallNode;

import bd.primitives.Primitive;
import bd.primitives.Specializer;
import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseCallbackMessage;
import som.interpreter.actors.ReceivedMessage.ReceivedCallback;
import som.interpreter.actors.RegisterOnPromiseNode.RegisterOnError;
import som.interpreter.actors.RegisterOnPromiseNode.RegisterWhenResolved;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.nary.BinaryComplexOperation.BinarySystemOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode.TernarySystemOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySystemOperation;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SSymbol;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.Tags.CreatePromisePair;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.concurrency.Tags.OnError;
import tools.concurrency.Tags.WhenResolved;
import tools.concurrency.Tags.WhenResolvedOnError;
import tools.debugger.entities.BreakpointType;
import tools.debugger.entities.SendOp;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.session.Breakpoints;


public final class PromisePrims {

  public static class IsActorModule extends Specializer<ExpressionNode, VM, ExpressionNode> {
    public IsActorModule(final Primitive prim, final NodeFactory<ExpressionNode> fact,
        final VM vm) {
      super(prim, fact, vm);
    }

    @Override
    public boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      // XXX: this is the case when doing parse-time specialization
      if (args == null) {
        return true;
      }

      return args[0] == ActorClasses.ActorModule;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "actorsCreatePromisePair:", selector = "createPromisePair",
      specializer = IsActorModule.class, noWrapper = true)
  public abstract static class CreatePromisePairPrim extends UnarySystemOperation {
    @Child protected AbstractBreakpointNode promiseResolverBreakpoint;
    @Child protected AbstractBreakpointNode promiseResolutionBreakpoint;

    protected static final DirectCallNode create() {
      Dispatchable disp = SPromise.pairClass.getSOMClass().lookupMessage(
          withAndFactory, AccessModifier.PUBLIC);
      return Truffle.getRuntime().createDirectCallNode(((SInvokable) disp).getCallTarget());
    }

    @Override
    public final CreatePromisePairPrim initialize(final VM vm) {
      super.initialize(vm);
      this.promiseResolverBreakpoint =
          insert(Breakpoints.create(sourceSection, BreakpointType.PROMISE_RESOLVER, vm));
      this.promiseResolutionBreakpoint =
          insert(Breakpoints.create(sourceSection, BreakpointType.PROMISE_RESOLUTION, vm));
      return this;
    }

    @Specialization
    public final SImmutableObject createPromisePair(final Object nil,
        @Cached("create()") final DirectCallNode factory) {

      SPromise promise = SPromise.createPromise(
          EventualMessage.getActorCurrentMessageIsExecutionOn(),
          promiseResolverBreakpoint.executeShouldHalt(),
          promiseResolutionBreakpoint.executeShouldHalt(),
          sourceSection);
      SResolver resolver = SPromise.createResolver(promise);
      return (SImmutableObject) factory.call(
          new Object[] {SPromise.pairClass, promise, resolver});
    }

    private static final SSymbol withAndFactory = Symbols.symbolFor("with:and:");

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == CreatePromisePair.class || tag == ExpressionBreakpoint.class
          || tag == StatementTag.class) {
        return true;
      }
      return super.isTaggedWithIgnoringEagerness(tag);
    }

  }

  // TODO: can we find another solution for megamorphics callers that
  // does not require node creation? Might need a generic received node.
  @TruffleBoundary
  public static RootCallTarget createReceived(final SBlock callback) {
    RootCallTarget target = callback.getMethod().getCallTarget();
    ReceivedCallback node = new ReceivedCallback(target);
    return Truffle.getRuntime().createCallTarget(node);
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsWhen:resolved:", selector = "whenResolved:",
      receiverType = SPromise.class)
  public abstract static class WhenResolvedPrim extends BinarySystemOperation {
    @Child protected RegisterWhenResolved registerNode;

    @Child protected AbstractBreakpointNode promiseResolverBreakpoint;
    @Child protected AbstractBreakpointNode promiseResolutionBreakpoint;

    @Override
    public final WhenResolvedPrim initialize(final VM vm) {
      super.initialize(vm);
      this.registerNode = new RegisterWhenResolved(vm.getActorPool());
      this.promiseResolverBreakpoint =
          insert(Breakpoints.create(sourceSection, BreakpointType.PROMISE_RESOLVER, vm));
      this.promiseResolutionBreakpoint =
          insert(Breakpoints.create(sourceSection, BreakpointType.PROMISE_RESOLUTION, vm));
      return this;
    }

    @Specialization(guards = "blockMethod == callback.getMethod()", limit = "10")
    public final SPromise whenResolved(final SPromise promise,
        final SBlock callback,
        @Cached("callback.getMethod()") final SInvokable blockMethod,
        @Cached("createReceived(callback)") final RootCallTarget blockCallTarget) {
      return registerWhenResolved(promise, callback, blockCallTarget, registerNode);
    }

    @Specialization(replaces = "whenResolved")
    public final SPromise whenResolvedUncached(final SPromise promise, final SBlock callback) {
      return registerWhenResolved(promise, callback, createReceived(callback), registerNode);
    }

    protected final SPromise registerWhenResolved(final SPromise rcvr,
        final SBlock block, final RootCallTarget blockCallTarget,
        final RegisterWhenResolved registerNode) {
      assert block.getMethod().getNumberOfArguments() == 2;

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();

      SPromise promise = SPromise.createPromise(current,
          false, promiseResolutionBreakpoint.executeShouldHalt(), sourceSection);
      SResolver resolver = SPromise.createResolver(promise);

      PromiseCallbackMessage pcm = new PromiseCallbackMessage(rcvr.getOwner(),
          block, resolver, blockCallTarget, false,
          promiseResolverBreakpoint.executeShouldHalt(), rcvr);
      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.sendOperation(SendOp.PROMISE_MSG, pcm.getMessageId(),
            rcvr.getPromiseId());
      }
      registerNode.register(rcvr, pcm, current);

      return promise;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == WhenResolved.class || tag == ExpressionBreakpoint.class
          || tag == StatementTag.class) {
        return true;
      }
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  // TODO: should I add a literal version of OnErrorPrim??
  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsFor:onError:", selector = "onError:")
  public abstract static class OnErrorPrim extends BinarySystemOperation {
    @Child protected RegisterOnError        registerNode;
    @Child protected AbstractBreakpointNode promiseResolverBreakpoint;
    @Child protected AbstractBreakpointNode promiseResolutionBreakpoint;

    @Override
    public final OnErrorPrim initialize(final VM vm) {
      super.initialize(vm);
      this.registerNode = new RegisterOnError(vm.getActorPool());
      this.promiseResolverBreakpoint =
          insert(Breakpoints.create(sourceSection, BreakpointType.PROMISE_RESOLVER, vm));
      this.promiseResolutionBreakpoint =
          insert(Breakpoints.create(sourceSection, BreakpointType.PROMISE_RESOLUTION, vm));
      return this;
    }

    @Specialization(guards = "blockMethod == callback.getMethod()", limit = "10")
    public final SPromise onError(final SPromise promise,
        final SBlock callback,
        @Cached("callback.getMethod()") final SInvokable blockMethod,
        @Cached("createReceived(callback)") final RootCallTarget blockCallTarget) {
      return registerOnError(promise, callback, blockCallTarget, registerNode);
    }

    @Specialization(replaces = "onError")
    public final SPromise whenResolvedUncached(final SPromise promise, final SBlock callback) {
      return registerOnError(promise, callback, createReceived(callback), registerNode);
    }

    protected final SPromise registerOnError(final SPromise rcvr,
        final SBlock block, final RootCallTarget blockCallTarget,
        final RegisterOnError registerNode) {
      assert block.getMethod().getNumberOfArguments() == 2;

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();

      SPromise promise = SPromise.createPromise(current,
          false, promiseResolutionBreakpoint.executeShouldHalt(), sourceSection);
      SResolver resolver = SPromise.createResolver(promise);

      PromiseCallbackMessage msg = new PromiseCallbackMessage(rcvr.getOwner(),
          block, resolver, blockCallTarget, false,
          promiseResolverBreakpoint.executeShouldHalt(), rcvr);
      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.sendOperation(SendOp.PROMISE_MSG, msg.getMessageId(),
            rcvr.getPromiseId());
      }
      registerNode.register(rcvr, msg, current);

      return promise;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OnError.class || tag == ExpressionBreakpoint.class
          || tag == StatementTag.class) {
        return true;
      }
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsWhen:resolved:onError:", selector = "whenResolved:onError:")
  public abstract static class WhenResolvedOnErrorPrim extends TernarySystemOperation {
    @Child protected RegisterWhenResolved registerWhenResolved;
    @Child protected RegisterOnError      registerOnError;

    @Child protected AbstractBreakpointNode promiseResolverBreakpoint;
    @Child protected AbstractBreakpointNode promiseResolutionBreakpoint;

    @Override
    public final WhenResolvedOnErrorPrim initialize(final VM vm) {
      this.promiseResolverBreakpoint =
          insert(Breakpoints.create(sourceSection, BreakpointType.PROMISE_RESOLVER, vm));
      this.promiseResolutionBreakpoint =
          insert(Breakpoints.create(sourceSection, BreakpointType.PROMISE_RESOLUTION, vm));
      this.registerWhenResolved = new RegisterWhenResolved(vm.getActorPool());
      this.registerOnError = new RegisterOnError(vm.getActorPool());
      return this;
    }

    @Specialization(guards = {"resolvedMethod == resolved.getMethod()",
        "errorMethod == error.getMethod()"})
    public final SPromise whenResolvedOnError(final SPromise promise,
        final SBlock resolved, final SBlock error,
        @Cached("resolved.getMethod()") final SInvokable resolvedMethod,
        @Cached("createReceived(resolved)") final RootCallTarget resolvedTarget,
        @Cached("error.getMethod()") final SInvokable errorMethod,
        @Cached("createReceived(error)") final RootCallTarget errorTarget) {
      return registerWhenResolvedOrError(promise, resolved, error, resolvedTarget,
          errorTarget, registerWhenResolved, registerOnError);
    }

    @Specialization(replaces = "whenResolvedOnError")
    public final SPromise whenResolvedOnErrorUncached(final SPromise promise,
        final SBlock resolved, final SBlock error) {
      return registerWhenResolvedOrError(promise, resolved, error, createReceived(resolved),
          createReceived(error), registerWhenResolved, registerOnError);
    }

    protected final SPromise registerWhenResolvedOrError(final SPromise rcvr,
        final SBlock resolved, final SBlock error,
        final RootCallTarget resolverTarget, final RootCallTarget errorTarget,
        final RegisterWhenResolved registerWhenResolved,
        final RegisterOnError registerOnError) {
      assert resolved.getMethod().getNumberOfArguments() == 2;
      assert error.getMethod().getNumberOfArguments() == 2;

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();

      SPromise promise = SPromise.createPromise(current,
          false, promiseResolutionBreakpoint.executeShouldHalt(), sourceSection);
      SResolver resolver = SPromise.createResolver(promise);

      PromiseCallbackMessage onResolved =
          new PromiseCallbackMessage(rcvr.getOwner(), resolved, resolver, resolverTarget,
              false, promiseResolverBreakpoint.executeShouldHalt(), rcvr);
      PromiseCallbackMessage onError = new PromiseCallbackMessage(rcvr.getOwner(), error,
          resolver, errorTarget, false, promiseResolverBreakpoint.executeShouldHalt(), rcvr);

      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.sendOperation(SendOp.PROMISE_MSG, onResolved.getMessageId(),
            rcvr.getPromiseId());
        ActorExecutionTrace.sendOperation(SendOp.PROMISE_MSG, onError.getMessageId(),
            rcvr.getPromiseId());
      }

      synchronized (rcvr) {
        registerWhenResolved.register(rcvr, onResolved, current);
        registerOnError.register(rcvr, onError, current);
      }
      return promise;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == WhenResolvedOnError.class || tag == ExpressionBreakpoint.class
          || tag == StatementTag.class) {
        return true;
      }
      return super.isTaggedWithIgnoringEagerness(tag);
    }

  }
}
