package som.instrumentation;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import tools.dym.Tags.CachedClosureInvoke;
import tools.dym.Tags.CachedVirtualInvoke;


@Instrumentable(factory = DirectCallNodeWrapper.class)
public class InstrumentableDirectCallNode extends DirectCallNode {

  @Child protected DirectCallNode         callNode;
  @CompilationFinal private SourceSection sourceSection;

  public InstrumentableDirectCallNode(final DirectCallNode callNode,
      final SourceSection source) {
    super(null);
    this.callNode = callNode;
    this.sourceSection = source;
  }

  protected InstrumentableDirectCallNode(final InstrumentableDirectCallNode wrapped) {
    super(null);
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == CachedVirtualInvoke.class) {
      return true;
    } else {
      return super.isTaggedWith(tag);
    }
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  @Override
  public Object call(final Object[] arguments) {
    return callNode.call(arguments);
  }

  @Override
  public CallTarget getCallTarget() {
    return callNode.getCallTarget();
  }

  @Override
  public boolean isInlinable() {
    return callNode.isInlinable();
  }

  @Override
  public boolean isInliningForced() {
    return callNode.isInliningForced();
  }

  @Override
  public void forceInlining() {
    callNode.forceInlining();
  }

  @Override
  public boolean isCallTargetCloningAllowed() {
    return callNode.isCallTargetCloningAllowed();
  }

  @Override
  public boolean cloneCallTarget() {
    return callNode.cloneCallTarget();
  }

  @Override
  public CallTarget getClonedCallTarget() {
    return callNode.getClonedCallTarget();
  }

  @Instrumentable(factory = DirectCallNodeWrapper.class)
  public static class InstrumentableBlockApplyNode extends InstrumentableDirectCallNode {
    public InstrumentableBlockApplyNode(final DirectCallNode callNode,
        final SourceSection source) {
      super(callNode, source);
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == CachedClosureInvoke.class) {
        return true;
      } else if (tag == CachedVirtualInvoke.class) {
        return false; // don't want that type of instrumentation here
      } else {
        return super.isTaggedWith(tag);
      }
    }
  }
}
