package som.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.profiles.ValueProfile;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.compiler.Variable.Argument;
import som.interpreter.InliningVisitor;
import som.interpreter.SArguments;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vmobjects.SSymbol;
import tools.Send;
import tools.debugger.Tags.ArgumentTag;
import tools.debugger.Tags.KeywordTag;
import tools.dym.Tags.LocalArgRead;


public abstract class ArgumentReadNode {

  @Instrumentable(factory = LocalArgumentReadNodeWrapper.class)
  public static class LocalArgumentReadNode extends ExprWithTagsNode implements Send {
    protected final int      argumentIndex;
    protected final Argument arg;

    public LocalArgumentReadNode(final Argument arg) {
      assert arg.index > 0 ||
          this instanceof LocalSelfReadNode ||
          this instanceof LocalSuperReadNode;
      this.argumentIndex = arg.index;
      this.arg = arg;
    }

    /** For Wrapper use only. */
    protected LocalArgumentReadNode(final LocalArgumentReadNode wrappedNode) {
      this.argumentIndex = wrappedNode.argumentIndex;
      this.arg = wrappedNode.arg;
    }

    /** For use in primitives only. */
    public LocalArgumentReadNode(final boolean insidePrim, final int argIdx) {
      this.argumentIndex = argIdx;
      this.arg = null;
      assert insidePrim : "Only to be used for primitive nodes";
    }

    public Argument getArg() {
      return arg;
    }

    @Override
    public final SSymbol getSelector() {
      return arg.name;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return SArguments.arg(frame, argumentIndex);
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

    @Override
    public void replaceAfterScopeChange(final InliningVisitor inliner) {
      inliner.updateRead(arg, this, 0);
    }
  }

  public static class LocalSelfReadNode extends LocalArgumentReadNode implements ISpecialSend {

    private final MixinDefinitionId mixin;
    private final ValueProfile      rcvrClass = ValueProfile.createClassProfile();

    public LocalSelfReadNode(final Argument arg, final MixinDefinitionId mixin) {
      super(arg);
      this.mixin = mixin;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return rcvrClass.profile(SArguments.rcvr(frame));
    }

    @Override
    public boolean isSuperSend() {
      return false;
    }

    @Override
    public MixinDefinitionId getEnclosingMixinId() {
      return mixin;
    }

    @Override
    public String toString() {
      return "LocalSelf";
    }

    @Override
    public void replaceAfterScopeChange(final InliningVisitor inliner) {
      inliner.updateSelfRead(arg, this, mixin, 0);
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

  public static class NonLocalArgumentReadNode extends ContextualNode implements Send {
    protected final int      argumentIndex;
    protected final Argument arg;

    public NonLocalArgumentReadNode(final Argument arg, final int contextLevel) {
      super(contextLevel);
      assert contextLevel > 0;
      assert arg.index > 0 ||
          this instanceof NonLocalSelfReadNode ||
          this instanceof NonLocalSuperReadNode;
      this.argumentIndex = arg.index;
      this.arg = arg;
    }

    public final Argument getArg() {
      return arg;
    }

    @Override
    public final SSymbol getSelector() {
      return arg.name;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return SArguments.arg(determineContext(frame), argumentIndex);
    }

    @Override
    public void replaceAfterScopeChange(final InliningVisitor inliner) {
      inliner.updateRead(arg, this, contextLevel);
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
        final int contextLevel) {
      super(arg, contextLevel);
      this.mixin = mixin;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return rcvrClass.profile(SArguments.rcvr(determineContext(frame)));
    }

    @Override
    public boolean isSuperSend() {
      return false;
    }

    @Override
    public MixinDefinitionId getEnclosingMixinId() {
      return mixin;
    }

    @Override
    public String toString() {
      return "NonLocalSelf";
    }

    @Override
    public void replaceAfterScopeChange(final InliningVisitor inliner) {
      inliner.updateSelfRead(arg, this, mixin, contextLevel);
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
    private final boolean           classSide;

    public LocalSuperReadNode(final Argument arg, final MixinDefinitionId holderMixin,
        final boolean classSide) {
      super(arg);
      this.holderMixin = holderMixin;
      this.classSide = classSide;
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
    public void replaceAfterScopeChange(final InliningVisitor inliner) {
      inliner.updateSuperRead(arg, this, holderMixin, classSide, 0);
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
    private final boolean           classSide;

    public NonLocalSuperReadNode(final Argument arg, final int contextLevel,
        final MixinDefinitionId holderMixin, final boolean classSide) {
      super(arg, contextLevel);
      this.holderMixin = holderMixin;
      this.classSide = classSide;
    }

    @Override
    public MixinDefinitionId getEnclosingMixinId() {
      return holderMixin;
    }

    @Override
    public void replaceAfterScopeChange(final InliningVisitor inliner) {
      inliner.updateSuperRead(arg, this, holderMixin, classSide, contextLevel);
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
