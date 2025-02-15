package somns.primitives;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import somns.interpreter.actors.SFarReference;
import somns.primitives.threading.TaskThreads.SomForkJoinTask;
import somns.primitives.threading.TaskThreads.SomThreadTask;
import somns.vmobjects.SBlock;
import somns.vmobjects.SInvokable;
import somns.vmobjects.SObjectWithClass;
import somns.vmobjects.SArray.SMutableArray;


@GenerateNodeFactory
@Primitive(primitive = "object:identicalTo:", selector = "==")
public abstract class EqualsEqualsPrim extends ComparisonPrim {
  @Specialization
  public final boolean doSBlock(final SBlock left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doArray(final SMutableArray left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doSMethod(final SInvokable left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doSObject(final SObjectWithClass left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doSFarReference(final SFarReference left, final SFarReference right) {
    return left.getValue() == right.getValue();
  }

  protected static final boolean notFarReference(final Object obj) {
    return !(obj instanceof SFarReference);
  }

  @Specialization(guards = "notFarReference(right)")
  public final boolean doFarRefAndObj(final SFarReference left, final Object right) {
    return false;
  }

  @Specialization
  public final boolean doThread(final SomThreadTask left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doThread(final SomForkJoinTask left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doMutex(final ReentrantLock left, final Object right) {
    return left == right;
  }

  @Specialization
  public final boolean doCondition(final Condition left, final Object right) {
    return left == right;
  }
}
