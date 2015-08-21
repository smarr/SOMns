package som.primitives;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.nodes.ISpecialSend;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;

import com.oracle.truffle.api.dsl.Specialization;


// This isn't a primitive anymore, because we do all this in the magic of the
//  primary factory methods, which are generated in assemblePrimaryFactoryMethod()
// TODO: figure out where we need to do that, or whether we could actually do it
// in the language
// @GenerateNodeFactory
//    @Primitive("instantiate:")
public abstract class NewObjectPrim extends UnaryExpressionNode implements ISpecialSend {
  private final MixinDefinitionId mixinId;

  public NewObjectPrim(final MixinDefinitionId mixinId) {
    this.mixinId = mixinId;
  }

  @Override
  public MixinDefinitionId getEnclosingMixinId() {
    return mixinId;
  }

  @Override
  public boolean isSuperSend() { return false; }

  @Specialization(guards = {"receiver.hasFields()", "receiver.hasOnlyImmutableFields()" })
  public final SAbstractObject doClassWithOnlyImmutableFields(final SClass receiver) {
    return new SImmutableObject(receiver);
  }

  @Specialization(guards = {"receiver.hasFields()", "!receiver.hasOnlyImmutableFields()" })
  public final SAbstractObject doClassWithFields(final SClass receiver) {
    return new SMutableObject(receiver);
  }

  @Specialization(guards = "!receiver.hasFields()")
  public final SAbstractObject doClassWithoutFields(final SClass receiver) {
    return new SObjectWithoutFields(receiver);
  }
}
