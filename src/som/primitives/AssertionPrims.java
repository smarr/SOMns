package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SArguments;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.vm.VmSettings;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SSymbol;
import tools.concurrency.Assertion;
import tools.concurrency.Assertion.FutureAssertion;
import tools.concurrency.Assertion.GloballyAssertion;
import tools.concurrency.Assertion.NextAssertion;
import tools.concurrency.Assertion.UntilAssertion;
import tools.concurrency.TracingActors.TracingActor;

public class AssertionPrims {

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assertNext:msg:")
  public abstract static class AssertNextPrim extends BinaryComplexOperation{

    protected AssertNextPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization (guards = "valueIsNil(msg)")
    public final Object doSBlock(final SBlock statement, final Object msg) {
      addAssertion(new NextAssertion(statement));
      return Nil.nilObject;
    }

    @Specialization
    public final Object doSBlockWithMessage(final SBlock statement, final String msg) {
      addAssertion(new NextAssertion(statement, msg));
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assertNow:msg:")
  public abstract static class AssertNowPrim extends BinaryComplexOperation{

    protected AssertNowPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization (guards = "valueIsNil(msg)")
    public final Object doBool(final boolean bool, final Object msg) {
      if (!VmSettings.ENABLE_ASSERTIONS) {
        return Nil.nilObject;
      }

      if (!bool) {
          throw new AssertionError();
      }

      return Nil.nilObject;
    }

    @Specialization
    public final Object doBool(final boolean bool, final String msg) {
      if (!VmSettings.ENABLE_ASSERTIONS) {
        return Nil.nilObject;
      }

      if (!bool) {
          throw new AssertionError(msg);
      }

      return Nil.nilObject;
    }

    @Specialization (guards = "valueIsNil(msg)")
    public final Object doSBlock(final SBlock statement, final Object msg) {
      if (!VmSettings.ENABLE_ASSERTIONS) {
        return Nil.nilObject;
      }

      if (!(boolean) statement.getMethod().invoke(new Object[] {statement})) {
          throw new AssertionError();
      }

      return Nil.nilObject;
    }

    @Specialization
    public final Object doSBlockWithMessage(final SBlock statement, final String msg) {
      if (!VmSettings.ENABLE_ASSERTIONS) {
        return Nil.nilObject;
      }

      if (!(boolean) statement.getMethod().invoke(new Object[] {statement})) {
          throw new AssertionError(msg);
      }

      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assertFuture:msg:")
  public abstract static class AssertFuturePrim extends BinaryComplexOperation{

    protected AssertFuturePrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization(guards = "valueIsNil(msg)")
    public final Object doSBlock(final SBlock statement, final Object msg) {
      if (VmSettings.ENABLE_ASSERTIONS) {
        addAssertion(new FutureAssertion(statement));
      }
      return Nil.nilObject;
    }

    @Specialization
    public final Object doSBlockWithMessage(final SBlock statement, final String msg) {
      if (VmSettings.ENABLE_ASSERTIONS) {
        addAssertion(new FutureAssertion(statement, msg));
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assertGlobally:msg:")
  public abstract static class AssertGloballyPrim extends BinaryComplexOperation{

    protected AssertGloballyPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization(guards = "valueIsNil(msg)")
    public final Object doSBlock(final SBlock statement, final Object msg) {
      addAssertion(new GloballyAssertion(statement));
      return Nil.nilObject;
    }

    @Specialization
    public final Object doSBlockWithMessage(final SBlock statement, final String msg) {
      addAssertion(new GloballyAssertion(statement, msg));
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assert:until:msg:")
  public abstract static class AssertUntilPrim extends TernaryExpressionNode{

    protected AssertUntilPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization(guards = "valueIsNil(msg)")
    public final Object doSBlock(final SBlock statement, final SBlock until, final Object msg) {
      addAssertion(new UntilAssertion(statement, until));
      return Nil.nilObject;
    }

    @Specialization
    public final Object doSBlockWithMessage(final SBlock statement, final SBlock until, final String msg) {
      addAssertion(new UntilAssertion(statement, until, msg));
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(Nil.class)
  @Primitive(primitive = "assert:release:msg:")
  public abstract static class AssertReleasePrim extends TernaryExpressionNode{

    protected AssertReleasePrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization(guards = "valueIsNil(msg)")
    public final Object doSBlock(final SBlock statement, final SBlock release, final Object msg) {
      addAssertion(new Assertion.ReleaseAssertion(statement, release));
      return Nil.nilObject;
    }

    @Specialization
    public final Object doSBlockWithMessage(final SBlock statement, final SBlock release, final String msg) {
      addAssertion(new Assertion.ReleaseAssertion(statement, release, msg));
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
  @Primitive(primitive = "assertResultUsed:")
  public abstract static class IsResultUsedPrim extends UnaryBasicOperation{

    protected IsResultUsedPrim(final boolean eagerlyWrapped, final SourceSection source) {
      super(eagerlyWrapped, source);
    }

    @Specialization
    public final Object dovoid(final Object receiver) {
      if (!VmSettings.ENABLE_ASSERTIONS) {
        return Nil.nilObject;
      }

      assert Thread.currentThread() instanceof ActorProcessingThread;
      EventualMessage current = EventualMessage.getCurrentExecutingMessage();
      if (current.getResolver() == null) {
        throw new AssertionError("Message result is unused");
      }

      if (!current.getResolver().getPromise().isResultUsed()) {
        getCurrentTracingActor().addAssertion(new Assertion.ResultUsedAssertion(current.getResolver().getPromise(), "Message result is unused"));
      }
      return null;
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

  private static void addAssertion(final Assertion assertion) {
    if (!VmSettings.ENABLE_ASSERTIONS) {
      return;
    }

    getCurrentTracingActor().addAssertion(assertion);
  }

  private static TracingActor getCurrentTracingActor() {
    assert Thread.currentThread() instanceof ActorProcessingThread;
    return (TracingActor) ((ActorProcessingThread) Thread.currentThread()).getCurrentlyExecutingActor();
  }
}
