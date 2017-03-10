package som.interpreter.nodes.specialized.whileloops;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.vm.VmSettings;
import som.vm.constants.Nil;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import tools.dym.Tags.LoopNode;


public abstract class WhileCache extends BinaryComplexOperation {
  public static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;

  protected final boolean predicateBool;

  public WhileCache(final SourceSection source, final boolean predicateBool) {
    super(false, source);
    this.predicateBool = predicateBool;
  }

  @Override
  protected boolean isTaggedWithIgnoringEagerness(final Class<?> tag) {
    if (tag == LoopNode.class) {
      return true;
    } else {
      return super.isTaggedWithIgnoringEagerness(tag);
    }
  }

  @Specialization(limit = "INLINE_CACHE_SIZE",
      guards = {"loopCondition.getMethod() == cachedLoopCondition",
                "loopBody.getMethod() == cachedLoopBody"})
  public final Object doCached(final SBlock loopCondition, final SBlock loopBody,
      @Cached("loopCondition.getMethod()") final SInvokable cachedLoopCondition,
      @Cached("loopBody.getMethod()") final      SInvokable cachedLoopBody,
      @Cached("create(loopCondition, loopBody, predicateBool)") final
         WhileWithDynamicBlocksNode whileNode) {
    return whileNode.doWhileUnconditionally(loopCondition, loopBody);
  }

  private boolean obj2bool(final Object o) {
    if (o instanceof Boolean) {
      return (boolean) o;
    }
    CompilerDirectives.transferToInterpreter();
    throw new RuntimeException("should never get here");
  }

  @Specialization(replaces = "doCached")
  public final Object doUncached(final SBlock loopCondition, final SBlock loopBody) {
    CompilerAsserts.neverPartOfCompilation("WhileCache.GenericDispatch"); // no caching, direct invokes, no loop count reporting...

    Object conditionResult = loopCondition.getMethod().invoke(new Object[] {loopCondition});

    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    boolean loopConditionResult = obj2bool(conditionResult);

    // TODO: this is a simplification, we don't cover the case receiver isn't a boolean
    while (loopConditionResult == predicateBool) {
      loopBody.getMethod().invoke(new Object[] {loopBody});
      conditionResult = loopCondition.getMethod().invoke(new Object[] {loopCondition});
      loopConditionResult = obj2bool(conditionResult);
      ObjectTransitionSafepoint.INSTANCE.checkAndPerformSafepoint();
    }
    return Nil.nilObject;
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    return false;
  }
}
