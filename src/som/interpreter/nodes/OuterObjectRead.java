package som.interpreter.nodes;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.VmSettings;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.objectstorage.ClassFactory;
import som.primitives.actors.ActorClasses;
import som.vm.constants.KernelObj;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;


@ImportStatic(ActorClasses.class)
@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class OuterObjectRead
    extends ExprWithTagsNode implements ISpecialSend {

  protected static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 3;

  /**
   * The level of lexical context. A level of 0 is self, level of 1 is the first
   * outer lexical context, so, the directly enclosing object.
   */
  protected final int contextLevel;

  /**
   * Mixin where the lookup starts, can be a mixin different from the `self`.
   * Thus, it can be one in the inheritance chain, for instance.
   */
  protected final MixinDefinitionId mixinId;

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

  public abstract Object executeEvaluated(Object receiver);

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
    return getEnclosingObject(lexicalClass);
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
    return getEnclosingObject(getEnclosingClassWithPotentialFailure(receiver, superclassIdx));
  }

  @Specialization(guards = {"contextLevel != 0"}, contains = "fixedLookup")
  public final Object fallback(final SObjectWithClass receiver) {
    return getEnclosingObject(getEnclosingClass(receiver));
  }

  @ExplodeLoop
  private Object getEnclosingObject(final SClass lexicalClass) {
    int ctxLevel = contextLevel - 1; // 0 is already covered with specialization
    SObjectWithClass enclosing = lexicalClass.getEnclosingObject();

    while (ctxLevel > 0) {
      ctxLevel--;
      enclosing = enclosing.getSOMClass().getEnclosingObject();
    }
    return enclosingObj.profile(enclosing);
  }

  /**
   * Need to get the outer scope for FarReference, while in the Actors module.
   */
  @Specialization(guards = {"mixinId == FarRefId", "contextLevel == 1"})
  public Object doFarReferenceInActorModuleScope(final SFarReference receiver) {
    assert ActorClasses.ActorModule != null;
    return ActorClasses.ActorModule;
  }

  /**
   * Need to get the outer scope for FarReference, but it is not the actors module.
   * Since there are only super classes in Kernel, we know it is going to be the Kernel.
   */
  @Specialization(guards = {"mixinId != FarRefId", "contextLevel == 1"})
  public Object doFarReferenceInKernelModuleScope(final SFarReference receiver) {
    return KernelObj.kernel;
  }

  /**
   * Need to get outer scope of contextLevel == 0, which is self.
   */
  @Specialization(guards = {"contextLevel == 0"})
  public SFarReference doFarReferenceDirect(final SFarReference receiver) {
    return receiver;
  }

  @Specialization(guards = "contextLevel != 0")
  public Object doBool(final boolean receiver) { return KernelObj.kernel; }

  @Specialization(guards = "contextLevel != 0")
  public Object doLong(final long receiver) { return KernelObj.kernel; }

  @Specialization(guards = "contextLevel != 0")
  public Object doString(final String receiver) { return KernelObj.kernel; }

  @Specialization(guards = "contextLevel != 0")
  public Object doBigInteger(final BigInteger receiver) { return KernelObj.kernel; }

  @Specialization(guards = "contextLevel != 0")
  public Object doDouble(final double receiver) { return KernelObj.kernel; }

  @Specialization(guards = "contextLevel != 0")
  public Object doSSymbol(final SSymbol receiver) { return KernelObj.kernel; }

  @Specialization(guards = "contextLevel != 0")
  public Object doSArray(final SArray receiver) { return KernelObj.kernel; }

  @Specialization(guards = "contextLevel != 0")
  public Object doSBlock(final SBlock receiver) { return KernelObj.kernel; }

  @Specialization(guards = "contextLevel == 0")
  public boolean doBoolDirect(final boolean receiver) { return receiver; }

  @Specialization(guards = "contextLevel == 0")
  public long doLongDirect(final long receiver) { return receiver; }

  @Specialization(guards = "contextLevel == 0")
  public String doStringDirect(final String receiver) { return receiver; }

  @Specialization(guards = "contextLevel == 0")
  public BigInteger doBigIntegerDirect(final BigInteger receiver) { return receiver; }

  @Specialization(guards = "contextLevel == 0")
  public Double doDoubleDirect(final double receiver) { return receiver; }

  @Specialization(guards = "contextLevel == 0")
  public SSymbol doSSymbolDirect(final SSymbol receiver) { return receiver; }

  @Specialization(guards = "contextLevel == 0")
  public SArray doSArrayDirect(final SArray receiver) { return receiver; }

  @Specialization(guards = "contextLevel == 0")
  public SBlock doSBlockDirect(final SBlock receiver) { return receiver; }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == StatementTag.class) {
      return isMarkedAsRootExpression();
    }
    return super.isTaggedWith(tag);
  }
}
