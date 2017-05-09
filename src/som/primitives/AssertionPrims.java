package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SArguments;
import som.interpreter.SomLanguage;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.ResolvePromiseNode;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.Resolution;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.actors.WrapReferenceNode;
import som.interpreter.actors.WrapReferenceNodeGen;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.VmSettings;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SSymbol;
import tools.concurrency.Assertion;
import tools.concurrency.Assertion.FutureAssertion;
import tools.concurrency.Assertion.GloballyAssertion;
import tools.concurrency.Assertion.NextAssertion;
import tools.concurrency.Assertion.RemoteAssertion;
import tools.concurrency.Assertion.UntilAssertion;
import tools.concurrency.TracingActors.TracingActor;

public class AssertionPrims {

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assertNext:res:")
  public abstract static class AssertNextPrim extends BinaryExpressionNode{

    protected AssertNextPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSBlock(final SBlock statement, final SResolver resolver) {
      addAssertion(getCurrentTracingActor(), new NextAssertion(statement, resolver));
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assertFuture:res:")
  public abstract static class AssertFuturePrim extends BinaryExpressionNode{

    protected AssertFuturePrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSBlock(final SBlock statement, final SResolver resolver) {
      if (VmSettings.ENABLE_ASSERTIONS) {
        addAssertion(getCurrentTracingActor(), new FutureAssertion(statement, resolver));
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assertGlobally:res:")
  public abstract static class AssertGloballyPrim extends BinaryExpressionNode{

    protected AssertGloballyPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSBlock(final SBlock statement, final SResolver resolver) {
      addAssertion(getCurrentTracingActor(), new GloballyAssertion(statement, resolver));
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assert:until:res:")
  public abstract static class AssertUntilPrim extends TernaryExpressionNode{

    protected AssertUntilPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSBlock(final SBlock statement, final SBlock until, final SResolver resolver) {
      addAssertion(getCurrentTracingActor(), new UntilAssertion(statement, resolver, until));
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assert:release:res:")
  public abstract static class AssertReleasePrim extends TernaryExpressionNode{

    protected AssertReleasePrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSBlock(final SBlock statement, final SBlock release, final SResolver resolver) {
      addAssertion(getCurrentTracingActor(), new Assertion.ReleaseAssertion(statement, resolver, release));
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assert:on:res:")
  public abstract static class AssertOnPrim extends TernaryExpressionNode{

    protected AssertOnPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSBlock(final SBlock statement, final SFarReference target, final SResolver resolver) {
      assert !statement.hasContext() : "assert:on: only supports blocks without context!";

      addAssertion((TracingActor) target.getActor(), new RemoteAssertion(statement, resolver, target.getValue()));
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "isMessage:")
  public abstract static class IsMessagePrim extends UnaryBasicOperation{

    protected IsMessagePrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSSymbol(final SSymbol selector) {
      assert Thread.currentThread() instanceof ActorProcessingThread;
      return EventualMessage.getCurrentExecutingMessage().getSelector().equals(selector);
    }

    @Specialization
    public final Object doString(final String messageType) {
      assert Thread.currentThread() instanceof ActorProcessingThread;
      return EventualMessage.getCurrentExecutingMessage().getSelector().getString().equals(messageType);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "getMessage:")
  public abstract static class GetMessagePrim extends UnaryBasicOperation{
    protected GetMessagePrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object dovoid(final Object receiver) {
      assert Thread.currentThread() instanceof ActorProcessingThread;
      return EventualMessage.getCurrentExecutingMessage().getSelector();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "getMessageArguments:")
  public abstract static class GetMessageArgumentsPrim extends UnaryBasicOperation{
    protected GetMessageArgumentsPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object dovoid(final Object receiver) {
      assert Thread.currentThread() instanceof ActorProcessingThread;
      return SArguments.getArgumentsWithoutReceiver(EventualMessage.getCurrentExecutingMessage().getArgs());
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "isSender:")
  public abstract static class IsSenderPrim extends UnaryBasicOperation{

    protected IsSenderPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSSymbol(final SSymbol actorClass) {
      assert Thread.currentThread() instanceof ActorProcessingThread;
      if (((TracingActor) EventualMessage.getCurrentExecutingMessage().getSender()).getActorType() == null) {
        return actorClass.toString().equals("#main");
      }
      return ((TracingActor) EventualMessage.getCurrentExecutingMessage().getSender()).getActorType().equals(actorClass);
    }

    @Specialization
    public final Object doReference(final SFarReference actor) {
      assert Thread.currentThread() instanceof ActorProcessingThread;
      return actor.getActor().equals(EventualMessage.getCurrentExecutingMessage().getSender());
    }

    @Specialization
    public final Object doString(final String actorType) {
      assert Thread.currentThread() instanceof ActorProcessingThread;
      return ((TracingActor) EventualMessage.getCurrentExecutingMessage().getSender()).getActorType().getString().equals(actorType);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "getSender:")
  public abstract static class GetSenderPrim extends UnaryBasicOperation{

    protected GetSenderPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object dovoid(final Object receiver) {
      assert Thread.currentThread() instanceof ActorProcessingThread;
      // TODO sending object
      return EventualMessage.getCurrentExecutingMessage().getSender();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "isPromiseComplete:")
  public abstract static class IsPromiseResolvedPrim extends UnaryBasicOperation{

    protected IsPromiseResolvedPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doPromise(final SPromise p) {
      return p.isCompleted();
    }

    @Specialization
    public final Object doResolver(final SResolver r) {
      return r.getPromise().isCompleted();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "isPromiseMessage:")
  public abstract static class IsPromiseMsgPrim extends UnaryBasicOperation{

    protected IsPromiseMsgPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object dovoid(final Object receiver) {
      assert Thread.currentThread() instanceof ActorProcessingThread;
      return EventualMessage.getCurrentExecutingMessage() instanceof PromiseMessage;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "isResultUsed:")
  public abstract static class IsResultUsedPrim extends UnaryBasicOperation{
    @Child protected WrapReferenceNode wrapper = WrapReferenceNodeGen.create();

    protected IsResultUsedPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object dovoid(final SResolver resolver) {
      if (!VmSettings.ENABLE_ASSERTIONS) {
        return Nil.nilObject;
      }

      assert Thread.currentThread() instanceof ActorProcessingThread;
      EventualMessage current = EventualMessage.getCurrentExecutingMessage();

      if (current.getResolver() == null) {
        ResolvePromiseNode.resolve(Resolution.SUCCESSFUL, wrapper,
            resolver.getPromise(), false,
            resolver.getPromise().getOwner(), this.getRootNode().getLanguage(SomLanguage.class).getVM().getActorPool(), false);
      } else if (current.getResolver().getPromise().isResultUsed()) {
        ResolvePromiseNode.resolve(Resolution.SUCCESSFUL, wrapper,
            resolver.getPromise(), true,
            resolver.getPromise().getOwner(), this.getRootNode().getLanguage(SomLanguage.class).getVM().getActorPool(), false);
      } else {
        getCurrentTracingActor().addAssertion(new Assertion.ResultUsedAssertion(current.getResolver().getPromise(), resolver));
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "AssertionsEnabled:")
  public abstract static class AssertionsEnabledPrim extends UnaryBasicOperation{
    protected AssertionsEnabledPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object dovoid(final Object o) {
      return VmSettings.ENABLE_ASSERTIONS;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "onSend:do:")
  public abstract static class OnSendPrim extends BinaryComplexOperation{

    protected OnSendPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSBlock(final SSymbol message, final SBlock aBlock) {
      if (!VmSettings.ENABLE_ASSERTIONS) {
        return Nil.nilObject;
      }

      getCurrentTracingActor().addSendHook(message, aBlock);

      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "onReceive:do:")
  public abstract static class OnReceivePrim extends BinaryComplexOperation{

    protected OnReceivePrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSBlock(final SSymbol message, final SBlock aBlock) {
      if (!VmSettings.ENABLE_ASSERTIONS) {
        return Nil.nilObject;
      }

      getCurrentTracingActor().addReceiveHook(message, aBlock);

      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "setAssertionModule:")
  public abstract static class SetAssertionModulePrim extends UnaryExpressionNode{

    protected SetAssertionModulePrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object doSBlock(final SImmutableObject assertionClass) {
      Assertion.setAssertionModule(assertionClass);
      return null;
    }
  }

  private static void addAssertion(final TracingActor target, final Assertion assertion) {
    if (!VmSettings.ENABLE_ASSERTIONS) {
      return;
    }

    target.addAssertion(assertion);
  }

  private static TracingActor getCurrentTracingActor() {
    assert Thread.currentThread() instanceof ActorProcessingThread;
    return (TracingActor) ((ActorProcessingThread) Thread.currentThread()).getCurrentlyExecutingActor();
  }
}
