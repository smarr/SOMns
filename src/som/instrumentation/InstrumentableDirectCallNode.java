package som.instrumentation;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import tools.dym.Tags.CachedClosureInvoke;
import tools.dym.Tags.CachedVirtualInvoke;


public class InstrumentableDirectCallNode extends DirectCallNode
    implements InstrumentableNode {

  @Child private DirectCallNode           callNode;
  @CompilationFinal private SourceSection sourceSection;

  public InstrumentableDirectCallNode(final DirectCallNode callNode,
      final SourceSection source) {
    super(null);
    assert (callNode != null && (getClass() == InstrumentableDirectCallNode.class
        || getClass() == InstrumentableBlockApplyNode.class))
        || (callNode == null) : "callNode needs to be set";
    this.callNode = callNode;
    this.sourceSection = source;
  }

  /** For wrapper. */
  protected InstrumentableDirectCallNode() {
    super(null);
  }

  public Object executeDummy(final VirtualFrame frame) {
    return null;
  }

  @Override
  public boolean isInstrumentable() {
    return true;
  }

  @Override
  public boolean hasTag(final Class<? extends Tag> tag) {
    return tag == CachedVirtualInvoke.class;
  }

  @Override
  public WrapperNode createWrapper(final ProbeNode probe) {
    return new InstrumentableDirectCallNodeWrapper(this, probe);
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

  public static class InstrumentableBlockApplyNode extends InstrumentableDirectCallNode {
    public InstrumentableBlockApplyNode(final DirectCallNode callNode,
        final SourceSection source) {
      super(callNode, source);
    }

    /** For wrappers. */
    protected InstrumentableBlockApplyNode() {
      this(null, null);
    }

    @Override
    public WrapperNode createWrapper(final ProbeNode probe) {
      return new InstrumentableBlockApplyNodeWrapper(this, probe);
    }

    @Override
    public boolean hasTag(final Class<? extends Tag> tag) {
      if (tag == CachedClosureInvoke.class) {
        return true;
      } else if (tag == CachedVirtualInvoke.class) {
        return false; // don't want that type of instrumentation here
      } else {
        return super.hasTag(tag);
      }
    }
  }
}
