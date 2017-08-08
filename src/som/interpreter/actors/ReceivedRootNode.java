package som.interpreter.actors;

import java.util.concurrent.ForkJoinPool;

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
        this.resolve = insert(ResolvePromiseNodeFactory.create(vm, null, null, null, null));
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
        this.error = insert(ErrorPromiseNodeFactory.create(vm, null, null, null, null));
      }
    }

    // error promise
    error.executeEvaluated(frame, resolver, exception, haltOnResolver, haltOnResolution);
  }

  /**
   * Promise resolver for the case that the actual promise has been optimized out.
   */
  public final class NullResolver extends AbstractPromiseResolutionNode {
    public NullResolver() {
      super((ForkJoinPool) null);
    }

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
