package som.primitives;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.SFarReference;
import som.vmobjects.SArray.SMutableArray;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObjectWithClass;


@GenerateNodeFactory
@Primitive(primitive = "object:identicalTo:", selector = "==")
public abstract class EqualsEqualsPrim extends ComparisonPrim {
  protected EqualsEqualsPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected EqualsEqualsPrim(final SourceSection source) { super(false, source); }

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
}
