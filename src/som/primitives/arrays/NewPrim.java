package som.primitives.arrays;

import java.util.Arrays;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.primitives.Primitive;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SClass;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


@GenerateNodeFactory
@Primitive("array:new:")
public abstract class NewPrim extends BinaryExpressionNode {

  protected final AllocProfile allocProfile;

  public NewPrim() { allocProfile = new AllocProfile(); }
  public NewPrim(final NewPrim clone) { allocProfile = clone.allocProfile; }

  public static class AllocProfile {
    public final Assumption assumption;
    @CompilationFinal private boolean becomesObject;

    public AllocProfile() {
      assumption = Truffle.getRuntime().createAssumption();
    }

    public boolean isBecomingObject() { return becomesObject; }
    public void doesBecomeObject() {
      if (!becomesObject) {
        CompilerDirectives.transferToInterpreter();
        allocationShouldAlwaysCreateObjectArrays();
      }
    }

    protected void allocationShouldAlwaysCreateObjectArrays() {
      CompilerAsserts.neverPartOfCompilation("Slow path: allocationShouldAlwaysCreateObjectArrays");
      assumption.invalidate();
      /* assumption = Truffle.getRuntime().createAssumption(); */
      becomesObject = true;
    }
  }

  protected static final boolean receiverIsArrayClass(final SClass receiver) {
    return receiver == Classes.arrayClass;
  }

  protected final boolean assumptionValid(final SClass rcvr) {
    return allocProfile.assumption.isValid();
  }

  // TODO: use:
  @Specialization(assumptions = "allocProfile.assumption",
      guards = {"assumptionValid(receiver)",
      "!allocProfile.isBecomingObject()", "receiverIsArrayClass(receiver)"})
  public final SArray doSpecializingArray(final SClass receiver, final long length) {
    return new SArray(length, allocProfile);
  }

  @Specialization(/* does not need the assumption, because it is not changing anymore.
                     assumptions = "allocProfile.assumption", */
      guards = {"allocProfile.isBecomingObject()", "receiverIsArrayClass(receiver)"})
  public final SArray doObjectArray(final SClass receiver, final long length) {
    Object[] storage = new Object[(int) length];
    Arrays.fill(storage, Nil.nilObject);
    return new SArray(true, storage);
  }
}
