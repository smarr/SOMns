package som.interpreter.nodes;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.actors.SFarReference;
import som.interpreter.objectstorage.ClassFactory;
import som.primitives.actors.ActorClasses;
import som.vm.constants.KernelObj;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.ValueProfile;

@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class OuterObjectRead
    extends ExpressionNode implements ISpecialSend {

  protected static final int INLINE_CACHE_SIZE = 3;

  protected final int contextLevel;
  private final MixinDefinitionId mixinId;
  private final MixinDefinitionId enclosingMixinId;

  private final ValueProfile enclosingObj;

  public OuterObjectRead(final int contextLevel,
      final MixinDefinitionId mixinId,
      final MixinDefinitionId enclosingMixinId,
      final SourceSection sourceSection) {
    super(sourceSection);
    this.contextLevel = contextLevel;
    this.mixinId = mixinId;
    this.enclosingMixinId = enclosingMixinId;
    this.enclosingObj = ValueProfile.createIdentityProfile();
  }

  public MixinDefinitionId getMixinId() {
    return mixinId;
  }

  @Override
  public MixinDefinitionId getEnclosingMixinId() {
    return enclosingMixinId;
  }

  @Override
  public boolean isSuperSend() { return false; }

  protected final SClass getEnclosingClass(final SObjectWithClass rcvr) {
    SClass lexicalClass = rcvr.getSOMClass().getClassCorrespondingTo(mixinId);
    assert lexicalClass != null;
    return lexicalClass;
  }

  protected static final SClass getEnclosingClassWithPotentialFailure(final SObjectWithClass rcvr,
      final int superclassIdx) {
    SClass lexicalClass = rcvr.getSOMClass().getClassCorrespondingTo(superclassIdx);
    return lexicalClass;
  }

  @Specialization(guards = "contextLevel == 0")
  public final Object doForOuterIsDirectClass(final SObjectWithClass receiver) {
    return enclosingObj.profile(receiver);
  }

  @Specialization(limit = "INLINE_CACHE_SIZE",
      guards = {"contextLevel != 0", "receiver.getSOMClass() == rcvrClass"})
  public final Object doForFurtherOuter(final SObjectWithClass receiver,
      @Cached("receiver.getSOMClass()") final SClass rcvrClass,
      @Cached("getEnclosingClass(receiver)") final SClass lexicalClass) {
    return getEnclosingObject(receiver, lexicalClass);
  }

  protected final int getIdx(final SObjectWithClass rcvr) {
    return rcvr.getSOMClass().getIdxForClassCorrespondingTo(mixinId);
  }

  protected boolean isSameEnclosingGroup(final SObjectWithClass receiver,
      final int superclassIdx, final ClassFactory factory) {
    SClass current = getEnclosingClassWithPotentialFailure(receiver, superclassIdx);
    if (current == null) {
      return false;
    }
    return current.getInstanceFactory() == factory;
  }

  @Specialization(guards = {"contextLevel != 0", "isSameEnclosingGroup(receiver, superclassIdx, factory)"},
      contains = "doForFurtherOuter")
  public final Object fixedLookup(final SObjectWithClass receiver,
      @Cached("getIdx(receiver)") final int superclassIdx,
      @Cached("getEnclosingClass(receiver).getInstanceFactory()") final ClassFactory factory) {
    assert factory != null;
    return getEnclosingObject(receiver, getEnclosingClassWithPotentialFailure(receiver, superclassIdx));
  }

  @Specialization(guards = {"contextLevel != 0"}, contains = "fixedLookup")
  public final Object fallback(final SObjectWithClass receiver) {
    return getEnclosingObject(receiver, getEnclosingClass(receiver));
  }

  public abstract Object executeEvaluated(Object receiver);

  protected static final boolean notSObjectWithClass(final Object receiver) {
    return !(receiver instanceof SObjectWithClass) && !(receiver instanceof SFarReference);
  }

  @Specialization(guards = "notSObjectWithClass(receiver)")
  public Object doObject(final Object receiver) {
    return KernelObj.kernel;
  }

  @Specialization
  public Object doFarReference(final SFarReference receiver) {
    assert ActorClasses.ActorModule != null;
    return ActorClasses.ActorModule;
  }

  @ExplodeLoop
  private Object getEnclosingObject(final SObjectWithClass receiver,
      final SClass lexicalClass) {
    int ctxLevel = contextLevel - 1; // 0 is already covered with specialization
    SObjectWithClass enclosing = lexicalClass.getEnclosingObject();

    while (ctxLevel > 0) {
      ctxLevel--;
      enclosing = enclosing.getSOMClass().getEnclosingObject();
    }
    return enclosingObj.profile(enclosing);
  }
}
