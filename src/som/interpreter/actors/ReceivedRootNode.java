package som.interpreter.actors;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.interpreter.SomLanguage;
import som.interpreter.actors.SPromise.SResolver;
import som.vm.VmSettings;
import tools.debugger.WebDebugger;


public abstract class ReceivedRootNode extends RootNode {

  @Child protected AbstractPromiseResolutionNode resolve;
  @Child protected AbstractPromiseResolutionNode error;

  private final VM vm;
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

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  protected final void resolvePromise(final VirtualFrame frame,
      final SResolver resolver, final Object result,
      final boolean isBreakpointOnPromiseResolution) {
    // lazy initialization of resolution node
    if (resolve == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      if (resolver == null) {
        this.resolve = insert(new NullResolver(getSourceSection()));
      } else {
        this.resolve = insert(ResolvePromiseNodeFactory.create(false, sourceSection, vm, null, null, null));
      }
    }

    // resolve promise
    resolve.executeEvaluated(frame, resolver, result, isBreakpointOnPromiseResolution);
  }

  protected final void errorPromise(final VirtualFrame frame,
      final SResolver resolver, final Object exception,
      final boolean isBreakpointOnPromiseResolution) {
    // lazy initialization of resolution node
    if (error == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      if (resolver == null) {
        this.error = insert(new NullResolver(getSourceSection()));
      } else {
        this.error = insert(ErrorPromiseNodeFactory.create(false, sourceSection, vm, null, null, null));
      }
    }

    // error promise
    error.executeEvaluated(frame, resolver, exception, isBreakpointOnPromiseResolution);
  }

  /**
   * Promise resolver for the case that the actual promise has been optimized out.
   */
  public final class NullResolver extends AbstractPromiseResolutionNode {
    public NullResolver(final SourceSection source) {
      super(false, source, null);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame,
        final SResolver receiver, final Object argument,
        final boolean isBreakpointOnPromiseResolution) {
      assert receiver == null;
      /* TODO: add a tag to the send node to disable or remove the button
       * if (isBreakpointOnResolutionAtRcvr) {} */
      return null;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver,
        final Object argument, final Object isBreakpointOnPromiseResolution) {
      return null;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return null;
    }
  }
}
