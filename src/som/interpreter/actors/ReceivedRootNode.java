package som.interpreter.actors;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.SArguments;
import som.interpreter.SomLanguage;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.VmSettings;
import tools.concurrency.MedeorTrace;
import tools.concurrency.nodes.TraceMessageNode;
import tools.concurrency.nodes.TraceMessageNodeGen;
import tools.debugger.WebDebugger;
import tools.debugger.entities.DynamicScopeType;


public abstract class ReceivedRootNode extends RootNode {

  @Child protected AbstractPromiseResolutionNode resolve;
  @Child protected AbstractPromiseResolutionNode error;

  @Child protected TraceMessageNode msgTracer = TraceMessageNodeGen.create();

  private final VM            vm;
  protected final WebDebugger dbg;
  private final SourceSection sourceSection;

  protected ReceivedRootNode(final SomLanguage language,
      final SourceSection sourceSection, final FrameDescriptor frameDescriptor) {
    super(language, frameDescriptor);
    assert sourceSection != null;
    this.vm = language.getVM();
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      this.dbg = vm.getWebDebugger();
    } else {
      this.dbg = null;
    }
    this.sourceSection = sourceSection;
  }

  protected abstract Object executeBody(VirtualFrame frame, EventualMessage msg,
      boolean haltOnResolver, boolean haltOnResolution);

  @Override
  public final Object execute(final VirtualFrame frame) {
    EventualMessage msg = (EventualMessage) SArguments.rcvr(frame);

    boolean haltOnResolver;
    boolean haltOnResolution;

    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      haltOnResolver = msg.getHaltOnResolver();
      haltOnResolution = msg.getHaltOnResolution();

      if (haltOnResolver) {
        dbg.prepareSteppingAfterNextRootNode();
      }
    } else {
      haltOnResolver = false;
      haltOnResolution = false;
    }

    if (VmSettings.MEDEOR_TRACING) {
      MedeorTrace.scopeStart(DynamicScopeType.TURN, msg.getMessageId(),
          msg.getTargetSourceSection());
    }

    try {
      return executeBody(frame, msg, haltOnResolver, haltOnResolution);
    } finally {
      if (VmSettings.ACTOR_TRACING) {
        msgTracer.execute(msg);
      }

      if (VmSettings.MEDEOR_TRACING) {
        MedeorTrace.scopeEnd(DynamicScopeType.TURN);
      }
    }
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  protected final void resolvePromise(final VirtualFrame frame,
      final SResolver resolver, final Object result,
      final boolean haltOnResolver, final boolean haltOnResolution) {
    // lazy initialization of resolution node
    if (resolve == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      if (resolver == null) {
        this.resolve = insert(new NullResolver());
      } else {
        this.resolve = insert(
            ResolvePromiseNodeFactory.create(null, null, null, null).initialize(vm));
      }
    }

    // resolve promise
    resolve.executeEvaluated(frame, resolver, result, haltOnResolver, haltOnResolution);
  }

  protected final void errorPromise(final VirtualFrame frame,
      final SResolver resolver, final Object exception,
      final boolean haltOnResolver, final boolean haltOnResolution) {
    // lazy initialization of resolution node
    if (error == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      if (resolver == null) {
        this.error = insert(new NullResolver());
      } else {
        this.error = insert(
            ErrorPromiseNodeFactory.create(null, null, null, null).initialize(vm));
      }
    }

    // error promise
    error.executeEvaluated(frame, resolver, exception, haltOnResolver, haltOnResolution);
  }

  /**
   * Promise resolver for the case that the actual promise has been optimized out.
   */
  public final class NullResolver extends AbstractPromiseResolutionNode {
    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final SResolver receiver, final Object argument,
        final boolean haltOnResolver, final boolean haltOnResolution) {
      assert receiver == null;
      return null;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object rcvr,
        final Object firstArg, final Object secondArg, final Object thirdArg) {
      return null;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return null;
    }
  }
}
