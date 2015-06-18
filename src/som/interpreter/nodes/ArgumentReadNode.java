package som.interpreter.nodes;

import som.compiler.ClassBuilder.ClassDefinitionId;
import som.interpreter.InlinerAdaptToEmbeddedOuterContext;
import som.interpreter.InlinerForLexicallyEmbeddedMethods;
import som.interpreter.SArguments;
import som.vm.NotYetImplementedException;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.ValueProfile;

public abstract class ArgumentReadNode {

  public static class LocalArgumentReadNode extends ExpressionNode {
    protected final int argumentIndex;

    public LocalArgumentReadNode(final int argumentIndex, final SourceSection source) {
      super(source);
      assert argumentIndex > 0 ||
        this instanceof LocalSelfReadNode ||
        this instanceof LocalSuperReadNode;
      this.argumentIndex = argumentIndex;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return SArguments.arg(frame, argumentIndex);
    }

    @Override
    public void replaceWithLexicallyEmbeddedNode(
        final InlinerForLexicallyEmbeddedMethods inliner) {
      replace(inliner.getReplacementForLocalArgument(argumentIndex,
          getSourceSection()));
    }

    @Override
    public String toString() {
      return "LocalArg(" + argumentIndex + ")";
    }
  }

  public static class LocalSelfReadNode extends LocalArgumentReadNode implements ISpecialSend {

    private final ClassDefinitionId lexicalClass;
    private final ValueProfile rcvrClass = ValueProfile.createClassProfile();

    public LocalSelfReadNode(final ClassDefinitionId lexicalClass,
        final SourceSection source) {
      super(0, source);
      this.lexicalClass = lexicalClass;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return rcvrClass.profile(SArguments.rcvr(frame));
    }

    @Override public boolean           isSuperSend()     { return false; }
    @Override public ClassDefinitionId getLexicalClass() { return lexicalClass; }
    @Override public String            toString()        { return "LocalSelf"; }

    @Override
    public void replaceWithLexicallyEmbeddedNode(
        final InlinerForLexicallyEmbeddedMethods inliner) {
      throw new NotYetImplementedException();
    }
  }

  public static class NonLocalArgumentReadNode extends ContextualNode {
    protected final int argumentIndex;

    public NonLocalArgumentReadNode(final int argumentIndex,
        final int contextLevel, final SourceSection source) {
      super(contextLevel, source);
      assert contextLevel > 0;
      assert argumentIndex > 0 ||
        this instanceof NonLocalSelfReadNode ||
        this instanceof NonLocalSuperReadNode;
      this.argumentIndex = argumentIndex;
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
      return new NonLocalArgumentReadNode(argumentIndex, contextLevel - 1,
          getSourceSection());
    }

    protected LocalArgumentReadNode createLocalNode() {
      return new LocalArgumentReadNode(argumentIndex, getSourceSection());
    }

    @Override
    public final void replaceWithCopyAdaptedToEmbeddedOuterContext(
        final InlinerAdaptToEmbeddedOuterContext inliner) {
      // this should be the access to a block argument
      if (inliner.appliesTo(contextLevel)) {
        assert !(this instanceof NonLocalSuperReadNode) && !(this instanceof NonLocalSelfReadNode);
        ExpressionNode node = inliner.getReplacementForBlockArgument(argumentIndex, getSourceSection());
        replace(node);
        return;
      } else if (inliner.needToAdjustLevel(contextLevel)) {
        // in the other cases, we just need to adjust the context level
        NonLocalArgumentReadNode node = createNonLocalNode();
        replace(node);
        return;
      }
    }
  }

  public static final class NonLocalSelfReadNode
      extends NonLocalArgumentReadNode implements ISpecialSend {
    private final ClassDefinitionId lexicalClass;

    private final ValueProfile rcvrClass = ValueProfile.createClassProfile();

    public NonLocalSelfReadNode(final ClassDefinitionId lexicalClass,
        final int contextLevel, final SourceSection source) {
      super(0, contextLevel, source);
      this.lexicalClass = lexicalClass;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return rcvrClass.profile(SArguments.rcvr(determineContext(frame)));
    }

    @Override public boolean           isSuperSend()     { return false; }
    @Override public ClassDefinitionId getLexicalClass() { return lexicalClass; }
    @Override public String            toString()        { return "NonLocalSelf"; }

    @Override
    protected NonLocalArgumentReadNode createNonLocalNode() {
      return new NonLocalSelfReadNode(lexicalClass, contextLevel - 1,
          getSourceSection());
    }

    @Override
    protected LocalArgumentReadNode createLocalNode() {
      return new LocalSelfReadNode(lexicalClass, getSourceSection());
    }
  }

  public static final class LocalSuperReadNode extends LocalArgumentReadNode
      implements ISuperReadNode {

    private final ClassDefinitionId holderClass;
    private final boolean classSide;

    public LocalSuperReadNode(final ClassDefinitionId holderClass,
        final boolean classSide, final SourceSection source) {
      super(SArguments.RCVR_IDX, source);
      this.holderClass = holderClass;
      this.classSide   = classSide;
    }

    @Override
    public ClassDefinitionId getLexicalClass() {
      return holderClass;
    }

    @Override
    public boolean isClassSide() {
      return classSide;
    }
  }

  public static final class NonLocalSuperReadNode extends
      NonLocalArgumentReadNode implements ISuperReadNode {

    private final ClassDefinitionId holderClass;
    private final boolean classSide;

    public NonLocalSuperReadNode(final int contextLevel,
        final ClassDefinitionId holderClass, final boolean classSide,
        final SourceSection source) {
      super(SArguments.RCVR_IDX, contextLevel, source);
      this.holderClass = holderClass;
      this.classSide   = classSide;
    }

    @Override
    public ClassDefinitionId getLexicalClass() {
      return holderClass;
    }

    @Override
    protected NonLocalArgumentReadNode createNonLocalNode() {
      return new NonLocalSuperReadNode(contextLevel - 1, holderClass,
          classSide, getSourceSection());
    }

    @Override
    protected LocalArgumentReadNode createLocalNode() {
      return new LocalSuperReadNode(holderClass, classSide, getSourceSection());
    }

    @Override
    public boolean isClassSide() {
      return classSide;
    }
  }
}
