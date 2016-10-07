package som.interpreter.actors;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.SPromise.SResolver;


public abstract class ReceivedRootNode extends RootNode {

  @Child protected ResolvePromiseNode resolve;

  protected ReceivedRootNode(final Class<? extends TruffleLanguage<?>> language,
      final SourceSection sourceSection, final FrameDescriptor frameDescriptor) {
    super(language, sourceSection, frameDescriptor);
    assert sourceSection != null;
  }

  protected final void resolvePromise(final VirtualFrame frame, final SResolver resolver, final Object result) {
    if (resolve == null) {
      if (resolver == null) {
        this.resolve = insert(new NullResolver(getSourceSection()));
      } else {
        this.resolve = insert(ResolvePromiseNodeFactory.create(false, getSourceSection(), null, null));
      }
    }

    resolve.executeEvaluated(frame, resolver, result);
  }

  public final class NullResolver extends ResolvePromiseNode {

    public NullResolver(final SourceSection source) {
      super(false, source);
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final SResolver receiver, final Object argument) {
      assert receiver == null;
      return null;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver,
        final Object argument) {
      return null;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return null;
    }

    @Override
    protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
      if (tag == StatementTag.class) {
        return isMarkedAsRootExpression();
      }
      return super.isTaggedWith(tag);
    }
  }
}
