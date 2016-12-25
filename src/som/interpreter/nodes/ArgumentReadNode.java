package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.Variable;
import som.compiler.Variable.Argument;
import som.interpreter.InlinerAdaptToEmbeddedOuterContext;
import som.interpreter.InlinerForLexicallyEmbeddedMethods;
import som.interpreter.SArguments;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vm.NotYetImplementedException;
import tools.debugger.Tags.ArgumentTag;
import tools.debugger.Tags.KeywordTag;
import tools.dym.Tags.LocalArgRead;


public abstract class ArgumentReadNode {

  @Instrumentable(factory = LocalArgumentReadNodeWrapper.class)
  public static class LocalArgumentReadNode extends ExprWithTagsNode {
    protected final int argumentIndex;
    protected final Argument arg;

    public LocalArgumentReadNode(final Argument arg, final SourceSection source) {
      super(source);
      assert arg.index > 0 ||
        this instanceof LocalSelfReadNode ||
        this instanceof LocalSuperReadNode;
      assert source != null;
      this.argumentIndex = arg.index;
      this.arg = arg;
    }

    /** For Wrapper use only. */
    protected LocalArgumentReadNode(final LocalArgumentReadNode wrappedNode) {
      super(wrappedNode);
      this.argumentIndex = wrappedNode.argumentIndex;
      this.arg = wrappedNode.arg;
    }

    /** For use in primitives only. */
    public LocalArgumentReadNode(final boolean insidePrim, final int argIdx, final SourceSection source) {
      super(source);
      this.argumentIndex = argIdx;
      this.arg = null;
      assert insidePrim : "Only to be used for primitive nodes";
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return SArguments.arg(frame, argumentIndex);
    }

    @Override
    public void replaceWithLexicallyEmbeddedNode(
        final InlinerForLexicallyEmbeddedMethods inliner) {
      Variable var = inliner.getSplitVar(arg);
      replace(var.getReadNode(0, sourceSection));
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == ArgumentTag.class) {
        return true;
      } else if (tag == LocalArgRead.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }

    @Override
    public String toString() {
      return "LocalArg(" + argumentIndex + ")";
    }
  }

  public static class LocalSelfReadNode extends LocalArgumentReadNode implements ISpecialSend {

    private final MixinDefinitionId mixin;
    private final ValueProfile rcvrClass = ValueProfile.createClassProfile();

    public LocalSelfReadNode(final Argument arg, final MixinDefinitionId mixin,
        final SourceSection source) {
      super(arg, source);
      this.mixin = mixin;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return rcvrClass.profile(SArguments.rcvr(frame));
    }

    @Override public boolean           isSuperSend() { return false; }
    @Override public MixinDefinitionId getEnclosingMixinId()  { return mixin; }
    @Override public String            toString()    { return "LocalSelf"; }

    @Override
    public void replaceWithLexicallyEmbeddedNode(
        final InlinerForLexicallyEmbeddedMethods inliner) {
      throw new NotYetImplementedException();
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == KeywordTag.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }
  }

  public static class NonLocalArgumentReadNode extends ContextualNode {
    protected final int argumentIndex;
    protected final Argument arg;

    public NonLocalArgumentReadNode(final Argument arg, final int contextLevel,
        final SourceSection source) {
      super(contextLevel, source);
      assert contextLevel > 0;
      assert arg.index > 0 ||
        this instanceof NonLocalSelfReadNode ||
        this instanceof NonLocalSuperReadNode;
      this.argumentIndex = arg.index;
      this.arg = arg;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return SArguments.arg(determineContext(frame), argumentIndex);
    }

    @Override
    public final void replaceWithLexicallyEmbeddedNode(
        final InlinerForLexicallyEmbeddedMethods inliner) {
      ExpressionNode inlined;
      if (contextLevel == 1) {
        inlined = createLocalNode();
      } else {
        inlined = createNonLocalNode();
      }
      replace(inlined);
    }

    protected NonLocalArgumentReadNode createNonLocalNode() {
      return new NonLocalArgumentReadNode(arg, contextLevel - 1, sourceSection);
    }

    protected LocalArgumentReadNode createLocalNode() {
      return new LocalArgumentReadNode(arg, sourceSection);
    }

    @Override
    public final void replaceWithCopyAdaptedToEmbeddedOuterContext(
        final InlinerAdaptToEmbeddedOuterContext inliner) {
      // this should be the access to a block argument
      ExpressionNode node;
      if (inliner.appliesTo(contextLevel)) {
        assert !(this instanceof NonLocalSuperReadNode) && !(this instanceof NonLocalSelfReadNode);
        Variable var = inliner.getSplitVar(arg);
        replace(var.getReadNode(argumentIndex, sourceSection));
      } else if (inliner.needToAdjustLevel(contextLevel)) {
        // in the other cases, we just need to adjust the context level
        node = createNonLocalNode();
        replace(node);
      }
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == ArgumentTag.class) {
        return true;
      } else if (tag == LocalArgRead.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }
  }

  public static final class NonLocalSelfReadNode
      extends NonLocalArgumentReadNode implements ISpecialSend {
    private final MixinDefinitionId mixin;

    private final ValueProfile rcvrClass = ValueProfile.createClassProfile();

    public NonLocalSelfReadNode(final Argument arg, final MixinDefinitionId mixin,
        final int contextLevel, final SourceSection source) {
      super(arg, contextLevel, source);
      this.mixin = mixin;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return rcvrClass.profile(SArguments.rcvr(determineContext(frame)));
    }

    @Override public boolean           isSuperSend() { return false; }
    @Override public MixinDefinitionId getEnclosingMixinId()  { return mixin; }
    @Override public String            toString()    { return "NonLocalSelf"; }

    @Override
    protected NonLocalArgumentReadNode createNonLocalNode() {
      return new NonLocalSelfReadNode(arg, mixin, contextLevel - 1, sourceSection);
    }

    @Override
    protected LocalArgumentReadNode createLocalNode() {
      return new LocalSelfReadNode(arg, mixin, sourceSection);
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == KeywordTag.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }
  }

  public static final class LocalSuperReadNode extends LocalArgumentReadNode
      implements ISuperReadNode {

    private final MixinDefinitionId holderMixin;
    private final boolean classSide;

    public LocalSuperReadNode(final Argument arg,
        final MixinDefinitionId holderMixin, final boolean classSide,
        final SourceSection source) {
      super(arg, source);
      this.holderMixin = holderMixin;
      this.classSide   = classSide;
    }

    @Override
    public MixinDefinitionId getEnclosingMixinId() {
      return holderMixin;
    }

    @Override
    public boolean isClassSide() {
      return classSide;
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == KeywordTag.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }
  }

  public static final class NonLocalSuperReadNode extends
      NonLocalArgumentReadNode implements ISuperReadNode {

    private final MixinDefinitionId holderMixin;
    private final boolean classSide;

    public NonLocalSuperReadNode(final Argument arg, final int contextLevel,
        final MixinDefinitionId holderMixin, final boolean classSide,
        final SourceSection source) {
      super(arg, contextLevel, source);
      this.holderMixin = holderMixin;
      this.classSide   = classSide;
    }

    @Override
    public MixinDefinitionId getEnclosingMixinId() {
      return holderMixin;
    }

    @Override
    protected NonLocalArgumentReadNode createNonLocalNode() {
      return new NonLocalSuperReadNode(arg, contextLevel - 1, holderMixin,
          classSide, sourceSection);
    }

    @Override
    protected LocalArgumentReadNode createLocalNode() {
      return new LocalSuperReadNode(arg, holderMixin, classSide, sourceSection);
    }

    @Override
    public boolean isClassSide() {
      return classSide;
    }

    @Override
    protected boolean isTaggedWith(final Class<?> tag) {
      if (tag == KeywordTag.class) {
        return true;
      } else {
        return super.isTaggedWith(tag);
      }
    }
  }
}
