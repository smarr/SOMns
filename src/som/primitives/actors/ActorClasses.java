package som.primitives.actors;

import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vmobjects.SClass;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;


public final class ActorClasses {
  @GenerateNodeFactory
  @Primitive("actorsFarReferenceClass:")
  public abstract static class SetFarReferenceClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SFarReference.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive("actorsPromiseClass:")
  public abstract static class SetPromiseClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SPromise.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive("actorsPairClass:")
  public abstract static class SetPairClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SPromise.setPairClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive("actorsResolverClass:")
  public abstract static class SetResolverClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SResolver.setSOMClass(value);
      return value;
    }
  }

  @CompilationFinal public static SObject ActorModule;

  @GenerateNodeFactory
  @Primitive("actorsModule:")
  public abstract static class SetModulePrim extends UnaryExpressionNode {
    @Specialization
    public final SObject setClass(final SObject value) {
      ActorModule = value;
      return value;
    }
  }
}
