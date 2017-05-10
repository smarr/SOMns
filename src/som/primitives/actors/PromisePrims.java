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
import com.oracle.truffle.api.source.SourceSection;

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
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vm.Primitives.Specializer;
import som.vm.Symbols;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SSymbol;
import tools.concurrency.Tags.CreatePromisePair;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.concurrency.Tags.OnError;
import tools.concurrency.Tags.WhenResolved;
import tools.concurrency.Tags.WhenResolvedOnError;
import tools.debugger.entities.BreakpointType;
import tools.debugger.nodes.AbstractBreakpointNode;
import tools.debugger.session.Breakpoints;


public final class PromisePrims {

  public static class IsActorModule extends Specializer<ExpressionNode> {
    public IsActorModule(final Primitive prim, final NodeFactory<ExpressionNode> fact, final VM vm) { super(prim, fact, vm); }

    @Override
    public boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      // XXX: this is the case when doing parse-time specialization
      if (args == null) { return true; }

      return args[0] == ActorClasses.ActorModule;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "actorsCreatePromisePair:", selector = "createPromisePair",
             specializer = IsActorModule.class, noWrapper = true, requiresContext = true)
  public abstract static class CreatePromisePairPrim extends UnaryExpressionNode {
    @Child protected AbstractBreakpointNode promiseResolverBreakpoint;
    @Child protected AbstractBreakpointNode promiseResolutionBreakpoint;

    protected static final DirectCallNode create() {
      Dispatchable disp = SPromise.pairClass.getSOMClass().lookupMessage(
          withAndFactory, AccessModifier.PUBLIC);
      return Truffle.getRuntime().createDirectCallNode(((SInvokable) disp).getCallTarget());
    }

    public CreatePromisePairPrim(final boolean eagerWrapper, final SourceSection source, final VM vm) {
      super(eagerWrapper, source);
      this.promiseResolverBreakpoint   = insert(Breakpoints.create(source, BreakpointType.PROMISE_RESOLVER, vm));
      this.promiseResolutionBreakpoint = insert(Breakpoints.create(source, BreakpointType.PROMISE_RESOLUTION, vm));
    }

    @Specialization
    public final SImmutableObject createPromisePair(final Object nil,
        @Cached("create()") final DirectCallNode factory) {

      SPromise promise   = SPromise.createPromise(
          EventualMessage.getActorCurrentMessageIsExecutionOn(),
          promiseResolutionBreakpoint.executeShouldHalt(),
          promiseResolverBreakpoint.executeShouldHalt(), true);
      SResolver resolver = SPromise.createResolver(promise);
      return (SImmutableObject) factory.call(new Object[] {SPromise.pairClass, promise, resolver});
    }

    private static final SSymbol withAndFactory = Symbols.symbolFor("with:and:");

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == CreatePromisePair.class || tag == ExpressionBreakpoint.class || tag == StatementTag.class) {
        return true;
      }
      return super.isTaggedWithIgnoringEagerness(tag);
    }

  }

  // TODO: can we find another solution for megamorphics callers that
  //       does not require node creation? Might need a generic received node.
  @TruffleBoundary
  public static RootCallTarget createReceived(final SBlock callback) {
    RootCallTarget target = callback.getMethod().getCallTarget();
    ReceivedCallback node = new ReceivedCallback(target);
    return Truffle.getRuntime().createCallTarget(node);
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsWhen:resolved:", selector = "whenResolved:",
             receiverType = SPromise.class, requiresContext = true)
  public abstract static class WhenResolvedPrim extends BinaryComplexOperation {
    @Child protected RegisterWhenResolved registerNode;

    @Child protected AbstractBreakpointNode promiseResolverBreakpoint;
    @Child protected AbstractBreakpointNode promiseResolutionBreakpoint;

    protected WhenResolvedPrim(final boolean eagWrap, final SourceSection source, final VM vm) {
      super(eagWrap, source);
      this.registerNode = new RegisterWhenResolved(vm.getActorPool());

      this.promiseResolverBreakpoint   = insert(Breakpoints.create(source, BreakpointType.PROMISE_RESOLVER, vm));
      this.promiseResolutionBreakpoint = insert(Breakpoints.create(source, BreakpointType.PROMISE_RESOLUTION, vm));
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

      SPromise  promise  = SPromise.createPromise(current,
          promiseResolutionBreakpoint.executeShouldHalt(), false, false);
      SResolver resolver = SPromise.createResolver(promise);

      PromiseCallbackMessage pcm = new PromiseCallbackMessage(EventualMessage.getCurrentExecutingMessageId(), rcvr.getOwner(),
          block, resolver, blockCallTarget, false, promiseResolverBreakpoint.executeShouldHalt(), rcvr);
      registerNode.register(rcvr, pcm, current);

      return promise;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == WhenResolved.class || tag == ExpressionBreakpoint.class || tag == StatementTag.class) {
        return true;
      }
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  // TODO: should I add a literal version of OnErrorPrim??
  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsFor:onError:", selector = "onError:", requiresContext = true)
  public abstract static class OnErrorPrim extends BinaryComplexOperation {
    @Child protected RegisterOnError registerNode;
    @Child protected AbstractBreakpointNode promiseResolverBreakpoint;
    @Child protected AbstractBreakpointNode promiseResolutionBreakpoint;

    protected OnErrorPrim(final boolean eagWrap, final SourceSection source, final VM vm) {
      super(eagWrap, source);
      this.registerNode = new RegisterOnError(vm.getActorPool());

      this.promiseResolverBreakpoint   = insert(Breakpoints.create(source, BreakpointType.PROMISE_RESOLVER, vm));
      this.promiseResolutionBreakpoint = insert(Breakpoints.create(source, BreakpointType.PROMISE_RESOLUTION, vm));
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

      SPromise  promise  = SPromise.createPromise(current,
          promiseResolutionBreakpoint.executeShouldHalt(), false, false);
      SResolver resolver = SPromise.createResolver(promise);

      PromiseCallbackMessage msg = new PromiseCallbackMessage(rcvr.getOwner(),
          block, resolver, blockCallTarget, false,
          promiseResolverBreakpoint.executeShouldHalt(), rcvr);
      registerNode.register(rcvr, msg, current);

      return promise;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == OnError.class || tag == ExpressionBreakpoint.class || tag == StatementTag.class) {
        return true;
      }
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "actorsWhen:resolved:onError:", selector = "whenResolved:onError:", requiresContext = true)
  public abstract static class WhenResolvedOnErrorPrim extends TernaryExpressionNode {
    @Child protected RegisterWhenResolved registerWhenResolved;
    @Child protected RegisterOnError      registerOnError;

    @Child protected AbstractBreakpointNode promiseResolverBreakpoint;
    @Child protected AbstractBreakpointNode promiseResolutionBreakpoint;

    public WhenResolvedOnErrorPrim(final boolean eagWrap, final SourceSection source, final VM vm) {
      super(eagWrap, source);
      this.registerWhenResolved = new RegisterWhenResolved(vm.getActorPool());
      this.registerOnError      = new RegisterOnError(vm.getActorPool());
      this.promiseResolverBreakpoint   = insert(Breakpoints.create(source, BreakpointType.PROMISE_RESOLVER, vm));
      this.promiseResolutionBreakpoint = insert(Breakpoints.create(source, BreakpointType.PROMISE_RESOLUTION, vm));
    }

    @Specialization(guards = {"resolvedMethod == resolved.getMethod()", "errorMethod == error.getMethod()"})
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
      assert error.getMethod().getNumberOfArguments()    == 2;

      Actor current = EventualMessage.getActorCurrentMessageIsExecutionOn();

      SPromise  promise  = SPromise.createPromise(current,
          promiseResolutionBreakpoint.executeShouldHalt(), false, false);
      SResolver resolver = SPromise.createResolver(promise);

      PromiseCallbackMessage onResolved = new PromiseCallbackMessage(rcvr.getOwner(), resolved, resolver, resolverTarget, false, promiseResolverBreakpoint.executeShouldHalt(), rcvr);
      PromiseCallbackMessage onError    = new PromiseCallbackMessage(rcvr.getOwner(), error,    resolver, errorTarget,    false, promiseResolverBreakpoint.executeShouldHalt(), rcvr);

      synchronized (rcvr) {
        registerWhenResolved.register(rcvr, onResolved, current);
        registerOnError.register(rcvr, onError, current);
      }
      return promise;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == WhenResolvedOnError.class || tag == ExpressionBreakpoint.class || tag == StatementTag.class) {
        return true;
      }
      return super.isTaggedWithIgnoringEagerness(tag);
    }

  }
}
