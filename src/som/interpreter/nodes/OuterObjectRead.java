package som.interpreter.nodes;

import java.math.BigInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.VmSettings;
import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.objectstorage.ClassFactory;
import som.primitives.actors.ActorClasses;
import som.primitives.threading.TaskPrimitives.SomForkJoinTask;
import som.primitives.threading.ThreadingModule;
import som.vm.constants.KernelObj;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;


@ImportStatic({ActorClasses.class, ThreadingModule.class})
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
    assert contextLevel > 0 : "A context level of 0 should be handled as a self-send.";
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

  @Specialization(limit = "INLINE_CACHE_SIZE",
      guards = {"receiver.getSOMClass() == rcvrClass"})
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

  @Specialization(guards = {"isSameEnclosingGroup(receiver, superclassIdx, factory)"},
      contains = "doForFurtherOuter")
  public final Object fixedLookup(final SObjectWithClass receiver,
      @Cached("getIdx(receiver)") final int superclassIdx,
      @Cached("getEnclosingClass(receiver).getInstanceFactory()") final ClassFactory factory) {
    assert factory != null;
    return getEnclosingObject(getEnclosingClassWithPotentialFailure(receiver, superclassIdx));
  }

  @Specialization(contains = "fixedLookup")
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

  @Specialization
  public Object doBool(final boolean receiver) { return KernelObj.kernel; }

  @Specialization
  public Object doLong(final long receiver) { return KernelObj.kernel; }

  @Specialization
  public Object doString(final String receiver) { return KernelObj.kernel; }

  @Specialization
  public Object doBigInteger(final BigInteger receiver) { return KernelObj.kernel; }

  @Specialization
  public Object doDouble(final double receiver) { return KernelObj.kernel; }

  @Specialization
  public Object doSSymbol(final SSymbol receiver) { return KernelObj.kernel; }

  @Specialization
  public Object doSArray(final SArray receiver) { return KernelObj.kernel; }

  @Specialization
  public Object doSBlock(final SBlock receiver) { return KernelObj.kernel; }

  @Specialization(guards = {"contextLevel == 1", "mixinId == ThreadClassId"})
  public Object doThread(final Thread receiver) {
    assert ThreadingModule.ThreadingModule != null;
    return ThreadingModule.ThreadingModule;
  }

  @Specialization(guards = {"contextLevel == 1", "mixinId == ConditionClassId"})
  public Object doCondition(final Condition receiver) {
    assert ThreadingModule.ThreadingModule != null;
    return ThreadingModule.ThreadingModule;
  }

  @Specialization(guards = {"contextLevel == 1", "mixinId == MutexClassId"})
  public Object doMutex(final ReentrantLock receiver) {
    assert ThreadingModule.ThreadingModule != null;
    return ThreadingModule.ThreadingModule;
  }

  @Specialization(guards = {"contextLevel == 1", "mixinId == TaskClassId"})
  public Object doTask(final SomForkJoinTask receiver) {
    assert ThreadingModule.ThreadingModule != null;
    return ThreadingModule.ThreadingModule;
  }

  @Specialization(guards = {"contextLevel == 1", "mixinId != ThreadClassId"})
  public Object doThreadInKernelScope(final Thread receiver) {
    return KernelObj.kernel;
  }

  @Specialization(guards = {"contextLevel == 1", "mixinId != ConditionClassId"})
  public Object doConditionInKernelScope(final Condition receiver) {
    return KernelObj.kernel;
  }

  @Specialization(guards = {"contextLevel == 1", "mixinId != MutexClassId"})
  public Object doMutexInKernelScope(final ReentrantLock receiver) {
    return KernelObj.kernel;
  }

  @Specialization(guards = {"contextLevel == 1", "mixinId != TaskClassId"})
  public Object doTaskInKernelScope(final SomForkJoinTask receiver) {
    return KernelObj.kernel;
  }
}
