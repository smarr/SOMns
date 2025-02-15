package somns.primitives.actors;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import somns.compiler.MixinBuilder.MixinDefinitionId;
import somns.interpreter.actors.SFarReference;
import somns.interpreter.actors.SPromise;
import somns.interpreter.actors.SPromise.SResolver;
import somns.interpreter.nodes.nary.UnaryExpressionNode;
import somns.interpreter.nodes.nary.UnaryExpressionNode.UnarySystemOperation;
import somns.primitives.TimerPrim;
import somns.vmobjects.SClass;
import somns.vmobjects.SObject.SImmutableObject;


public final class ActorClasses {

  @CompilationFinal public static SImmutableObject  ActorModule;
  @CompilationFinal public static MixinDefinitionId FarRefId;

  @GenerateNodeFactory
  @Primitive(primitive = "actorsFarReferenceClass:")
  public abstract static class SetFarReferenceClassPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final SClass setClass(final SClass value) {
      SFarReference.setSOMClass(value);
      FarRefId = value.getMixinDefinition().getMixinId();
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "actorsPromiseClass:")
  public abstract static class SetPromiseClassPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final SClass setClass(final SClass value) {
      SPromise.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "actorsPairClass:")
  public abstract static class SetPairClassPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final SClass setClass(final SClass value) {
      SPromise.setPairClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "actorsResolverClass:")
  public abstract static class SetResolverClassPrim extends UnaryExpressionNode {
    @Specialization
    @TruffleBoundary
    public final SClass setClass(final SClass value) {
      SResolver.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "actorsModule:")
  public abstract static class SetModulePrim extends UnarySystemOperation {
    @Specialization
    @TruffleBoundary
    public final SImmutableObject setClass(final SImmutableObject value) {
      ActorModule = value;
      TimerPrim.initializeTimer(vm);
      return value;
    }
  }
}
