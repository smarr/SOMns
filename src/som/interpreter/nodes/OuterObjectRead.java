package som.interpreter.nodes;

import java.math.BigInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ValueProfile;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.actors.SFarReference;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.processes.SChannel;
import som.interpreter.processes.SChannel.SChannelInput;
import som.interpreter.processes.SChannel.SChannelOutput;
import som.primitives.actors.ActorClasses;
import som.primitives.processes.ChannelPrimitives;
import som.primitives.threading.TaskThreads.SomForkJoinTask;
import som.primitives.threading.TaskThreads.SomThreadTask;
import som.primitives.threading.ThreadingModule;
import som.vm.VmSettings;
import som.vm.constants.KernelObj;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;


/**
 * The OuterObjectRead is responsible for obtaining the object enclosing the current one.
 *
 * <p>
 * Two mixin identities define the lexical elements with respect to which the outer object read
 * should be performed. Outer reads are lexically scoped, thus, they refer to the outer object
 * of to the class that surrounds the read in the source code.
 * The outer class of {@code self} can be different, because self might be a subclass
 * of the class an which the method with this outer read is defined.
 *
 * <p>
 * To find the relevant class/superclass of {@code self}, we use the {@code mixinId}.
 * {@code enclosingMixinId} describes the lexical class of the object that we wish to find.
 */
@ImportStatic({ActorClasses.class, ThreadingModule.class,
    ChannelPrimitives.class})
@NodeChild(value = "receiver", type = ExpressionNode.class)
public abstract class OuterObjectRead
    extends ExprWithTagsNode implements ISpecialSend {

  protected static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 3;

  /**
   * Mixin id for {@code self} (the receiver) or of one its superclasses.
   */
  protected final MixinDefinitionId mixinId;

  /**
   * The identity of the lexical class that we want to find, which
   * encloses either {@code self} or one of its superclasses. {@code mixinId} is used
   * to distinguish the two where necessary.
   */
  private final MixinDefinitionId enclosingMixinId;

  private final ValueProfile enclosingObj;

  public OuterObjectRead(final MixinDefinitionId mixinId,
      final MixinDefinitionId enclosingMixinId) {
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
  public boolean isSuperSend() {
    return false;
  }

  public abstract ExpressionNode getReceiver();

  protected abstract Object executeEvaluated(Object receiver);

  public Object computeOuter(final Object receiver) {
    ExpressionNode rcvr = getReceiver();

    Object current = receiver;
    // if we have a chain of outer nodes, we need to traverse it
    // otherwise, we are at the point where a ready have the pre-computed receiver
    if (rcvr instanceof OuterObjectRead) {
      current = ((OuterObjectRead) rcvr).computeOuter(receiver);
    }
    return executeEvaluated(current);
  }

  /**
   * The enclosing class can be either the class of the given receiver
   * or any of its superclasses. The receiver can denote either a class
   * or a mixin and, consequently, we need to check both the inheritance
   * chain and any mixins applied to that class (or one of its superclasses).
   * The class and its superclasses are queried first, and if no
   * class was found then all mixins applied along the inheritance chain are
   * queried.
   *
   * Note that this method should always find a class for the given receiver
   * and that the return value must be either the receiver's class or one its
   * superclasses (and not the class of a mixin).
   */
  protected final SClass getEnclosingClass(final SObjectWithClass rcvr) {
    SClass rcvrClass = rcvr.getSOMClass();
    SClass lexicalClass = rcvrClass.lookupClass(mixinId);
    if (lexicalClass == null) {
      lexicalClass = rcvrClass.lookupClassWithMixinApplied(mixinId);
    }
    assert lexicalClass != null;
    return lexicalClass;
  }

  private Object getEnclosingObject(final SClass lexicalClass) {
    SObjectWithClass enclosing = lexicalClass.getEnclosingObject();
    return enclosingObj.profile(enclosing);
  }

  protected static final SClass getEnclosingClassWithPotentialFailure(
      final SObjectWithClass rcvr, final int superclassIdx) {
    SClass lexicalClass = rcvr.getSOMClass().lookupClass(superclassIdx);
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
      replaces = "doForFurtherOuter")
  public final Object fixedLookup(final SObjectWithClass receiver,
      @Cached("getIdx(receiver)") final int superclassIdx,
      @Cached("getEnclosingClass(receiver).getInstanceFactory()") final ClassFactory factory) {
    assert factory != null;
    return getEnclosingObject(getEnclosingClassWithPotentialFailure(receiver, superclassIdx));
  }

  @Specialization(replaces = "fixedLookup")
  public final Object fallback(final SObjectWithClass receiver) {
    return getEnclosingObject(getEnclosingClass(receiver));
  }

  /**
   * Need to get the outer scope for FarReference, while in the Actors module.
   */
  @Specialization(guards = {"mixinId == FarRefId"})
  public Object doFarReferenceInActorModuleScope(final SFarReference receiver) {
    assert ActorClasses.ActorModule != null;
    return ActorClasses.ActorModule;
  }

  /**
   * Need to get the outer scope for FarReference, but it is not the actors module.
   * Since there are only super classes in Kernel, we know it is going to be the Kernel.
   */
  @Specialization(guards = {"mixinId != FarRefId"})
  public Object doFarReferenceInKernelModuleScope(final SFarReference receiver) {
    return KernelObj.kernel;
  }

  @Specialization
  public Object doBool(final boolean receiver) {
    return KernelObj.kernel;
  }

  @Specialization
  public Object doLong(final long receiver) {
    return KernelObj.kernel;
  }

  @Specialization
  public Object doString(final String receiver) {
    return KernelObj.kernel;
  }

  @Specialization
  public Object doBigInteger(final BigInteger receiver) {
    return KernelObj.kernel;
  }

  @Specialization
  public Object doDouble(final double receiver) {
    return KernelObj.kernel;
  }

  @Specialization
  public Object doSSymbol(final SSymbol receiver) {
    return KernelObj.kernel;
  }

  @Specialization
  public Object doSArray(final SArray receiver) {
    return KernelObj.kernel;
  }

  @Specialization
  public Object doSBlock(final SBlock receiver) {
    return KernelObj.kernel;
  }

  @Specialization(guards = {"mixinId == ThreadClassId"})
  public Object doThread(final SomThreadTask receiver) {
    assert ThreadingModule.ThreadingModule != null;
    return ThreadingModule.ThreadingModule;
  }

  @Specialization(guards = {"mixinId == ConditionClassId"})
  public Object doCondition(final Condition receiver) {
    assert ThreadingModule.ThreadingModule != null;
    return ThreadingModule.ThreadingModule;
  }

  @Specialization(guards = {"mixinId == MutexClassId"})
  public Object doMutex(final ReentrantLock receiver) {
    assert ThreadingModule.ThreadingModule != null;
    return ThreadingModule.ThreadingModule;
  }

  @Specialization(guards = {"mixinId == TaskClassId"})
  public Object doTask(final SomForkJoinTask receiver) {
    assert ThreadingModule.ThreadingModule != null;
    return ThreadingModule.ThreadingModule;
  }

  @Specialization(guards = {"mixinId == ChannelId"})
  public Object doChannel(final SChannel receiver) {
    assert ChannelPrimitives.ProcessesModule != null;
    return ChannelPrimitives.ProcessesModule;
  }

  @Specialization(guards = {"mixinId == InId"})
  public Object doChannel(final SChannelInput receiver) {
    assert ChannelPrimitives.ProcessesModule != null;
    return ChannelPrimitives.ProcessesModule;
  }

  @Specialization(guards = {"mixinId == OutId"})
  public Object doChannel(final SChannelOutput receiver) {
    assert ChannelPrimitives.ProcessesModule != null;
    return ChannelPrimitives.ProcessesModule;
  }

  @Specialization(guards = {"mixinId != ThreadClassId"})
  public Object doThreadInKernelScope(final SomThreadTask receiver) {
    return KernelObj.kernel;
  }

  @Specialization(guards = {"mixinId != ConditionClassId"})
  public Object doConditionInKernelScope(final Condition receiver) {
    return KernelObj.kernel;
  }

  @Specialization(guards = {"mixinId != MutexClassId"})
  public Object doMutexInKernelScope(final ReentrantLock receiver) {
    return KernelObj.kernel;
  }

  @Specialization(guards = {"mixinId != TaskClassId"})
  public Object doTaskInKernelScope(final SomForkJoinTask receiver) {
    return KernelObj.kernel;
  }

  @Specialization(guards = {"mixinId != ChannelId"})
  public Object doChannelInKernelScope(final SChannel receiver) {
    return KernelObj.kernel;
  }

  @Specialization(guards = {"mixinId != InId"})
  public Object doChannelInKernelScope(final SChannelInput receiver) {
    return KernelObj.kernel;
  }

  @Specialization(guards = {"mixinId != OutId"})
  public Object doChannelInKernelScope(final SChannelOutput receiver) {
    return KernelObj.kernel;
  }
}
