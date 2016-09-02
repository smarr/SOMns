package som.primitives.actors;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.Primitive;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SImmutableObject;


public final class ActorClasses {
  @GenerateNodeFactory
  @Primitive(primitive = "actorsFarReferenceClass:")
  public abstract static class SetFarReferenceClassPrim extends UnaryExpressionNode {
    public SetFarReferenceClassPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SClass setClass(final SClass value) {
      SFarReference.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "actorsPromiseClass:")
  public abstract static class SetPromiseClassPrim extends UnaryExpressionNode {
    public SetPromiseClassPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SClass setClass(final SClass value) {
      SPromise.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "actorsPairClass:")
  public abstract static class SetPairClassPrim extends UnaryExpressionNode {
    public SetPairClassPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SClass setClass(final SClass value) {
      SPromise.setPairClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "actorsResolverClass:")
  public abstract static class SetResolverClassPrim extends UnaryExpressionNode {
    public SetResolverClassPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SClass setClass(final SClass value) {
      SResolver.setSOMClass(value);
      return value;
    }
  }

  @CompilationFinal public static SImmutableObject ActorModule;

  @GenerateNodeFactory
  @Primitive(primitive = "actorsModule:")
  public abstract static class SetModulePrim extends UnaryExpressionNode {
    public SetModulePrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SImmutableObject setClass(final SImmutableObject value) {
      ActorModule = value;
      return value;
    }
  }
}
