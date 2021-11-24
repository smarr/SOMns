package som.interpreter.actors;

import java.util.concurrent.atomic.AtomicLong;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.SArguments;
import som.interpreter.SomLanguage;
import som.interpreter.actors.EventualMessage.PromiseMessage;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.VmSettings;
import tools.concurrency.KomposTrace;
import tools.concurrency.TracingActors.TracingActor;
import tools.debugger.WebDebugger;
import tools.debugger.entities.DynamicScopeType;
import tools.dym.DynamicMetrics;
import tools.replay.TraceRecord;
import tools.replay.nodes.RecordEventNodes.RecordOneEvent;
import tools.snapshot.nodes.MessageSerializationNode;
import tools.snapshot.nodes.MessageSerializationNodeFactory;


public abstract class ReceivedRootNode extends RootNode {

  @Child protected AbstractPromiseResolutionNode resolve;
  @Child protected AbstractPromiseResolutionNode error;

  @Child protected RecordOneEvent           messageTracer;
  @Child protected RecordOneEvent           promiseMessageTracer;
  @Child protected MessageSerializationNode serializer;

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
    if (VmSettings.SNAPSHOTS_ENABLED) {
      serializer = MessageSerializationNodeFactory.create();
    } else {
      serializer = null;
    }

    if (VmSettings.UNIFORM_TRACING) {
      messageTracer = new RecordOneEvent(TraceRecord.MESSAGE);
    }
    if (VmSettings.RECEIVER_SIDE_TRACING) {
      promiseMessageTracer = new RecordOneEvent(TraceRecord.PROMISE_MESSAGE);
    }
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
        dbg.prepareSteppingAfterNextRootNode(Thread.currentThread());
      }
    } else {
      haltOnResolver = false;
      haltOnResolution = false;
    }

    if (VmSettings.KOMPOS_TRACING) {
      KomposTrace.scopeStart(DynamicScopeType.TURN, msg.getMessageId(),
          msg.getTargetSourceSection());
    }

    if (VmSettings.RECEIVER_SIDE_TRACING) {
      if (msg instanceof PromiseMessage) {
        promiseMessageTracer.record(msg.messageId);
      }
      messageTracer.record(((TracingActor) msg.getSender()).getId());
    }

    try {
      return executeBody(frame, msg, haltOnResolver, haltOnResolution);
    } finally {
      if (VmSettings.KOMPOS_TRACING) {
        KomposTrace.scopeEnd(DynamicScopeType.TURN);
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
      // Warning: this is racy, thus, we do everything on a local,
      // and only publish the result at the end
      AbstractPromiseResolutionNode resolve;
      if (resolver == null) {
        resolve = insert(new NullResolver());
      } else {
        resolve = insert(
            ResolvePromiseNodeFactory.create(null, null, null, null).initialize(vm));
      }
      resolve.initialize(sourceSection);
      this.resolve = resolve;
      notifyInserted(this.resolve);
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
      // Warning: this is racy, thus, we do everything on a local,
      // and only publish the result at the end
      AbstractPromiseResolutionNode error;
      if (resolver == null) {
        error = insert(new NullResolver());
      } else {
        error = insert(
            ErrorPromiseNodeFactory.create(null, null, null, null).initialize(vm));
      }
      this.error = error;
      this.error.initialize(sourceSection);
      notifyInserted(this.error);
    }

    // error promise
    error.executeEvaluated(frame, resolver, exception, haltOnResolver, haltOnResolution);
  }

  public MessageSerializationNode getSerializer() {
    return serializer;
  }

  /**
   * Promise resolver for the case that the actual promise has been optimized out.
   */
  public static final class NullResolver extends AbstractPromiseResolutionNode {
    private static final AtomicLong numImplicitNullResolutions =
        DynamicMetrics.createLong("Num.ImplicitNullResolutions");

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final SResolver receiver, final Object argument,
        final boolean haltOnResolver, final boolean haltOnResolution) {
      assert receiver == null;
      if (VmSettings.DYNAMIC_METRICS) {
        numImplicitNullResolutions.getAndIncrement();
      }
      return null;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object rcvr,
        final Object firstArg, final Object secondArg, final Object thirdArg) {
      if (VmSettings.DYNAMIC_METRICS) {
        numImplicitNullResolutions.getAndIncrement();
      }
      return null;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      if (VmSettings.DYNAMIC_METRICS) {
        numImplicitNullResolutions.getAndIncrement();
      }
      return null;
    }
  }
}
