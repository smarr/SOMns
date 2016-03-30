package som.primitives;

import som.compiler.MixinBuilder.MixinDefinitionId;
import som.interpreter.nodes.ISpecialSend;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.objectstorage.ClassFactory;
import som.interpreter.objectstorage.ObjectLayout;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SImmutableObject;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;


// This isn't a primitive anymore, because we do all this in the magic of the
//  primary factory methods, which are generated in assemblePrimaryFactoryMethod()
// TODO: figure out where we need to do that, or whether we could actually do it
// in the language
// @GenerateNodeFactory
//    @Primitive("instantiate:")
public abstract class NewObjectPrim extends UnaryExpressionNode implements ISpecialSend {
  private final MixinDefinitionId mixinId;

  public NewObjectPrim(final SourceSection source, final MixinDefinitionId mixinId) {
    super(source);
    this.mixinId = mixinId;
  }

  @Override
  public MixinDefinitionId getEnclosingMixinId() {
    return mixinId;
  }

  @Override
  public boolean isSuperSend() { return false; }

  @Specialization(guards = {
      "receiver.getInstanceFactory() == factory",
      "factory.hasSlots()",
      "factory.hasOnlyImmutableFields()",
      "receiver.getInstanceFactory().getInstanceLayout() == layout"})
  public final SAbstractObject doClassWithOnlyImmutableFields(final SClass receiver,
      @Cached("receiver.getInstanceFactory()") final ClassFactory factory,
      @Cached("receiver.getInstanceFactory().getInstanceLayout()") final ObjectLayout layout) {
    return new SImmutableObject(receiver, factory, layout);
  }

  @Specialization(guards = {
      "receiver.getInstanceFactory() == factory",
      "factory.hasSlots()",
      "!factory.hasOnlyImmutableFields()",
      "receiver.getInstanceFactory().getInstanceLayout() == layout"})
  public final SAbstractObject doClassWithFields(
      final SClass receiver,
      @Cached("receiver.getInstanceFactory()") final ClassFactory factory,
      @Cached("factory.getInstanceLayout()") final ObjectLayout layout) {
    return new SMutableObject(receiver, factory, layout);
  }

  @Specialization(guards = {
      "receiver.getInstanceFactory() == factory",
      "!factory.hasSlots()"})
  public final SAbstractObject doClassWithoutFields(final SClass receiver,
      @Cached("receiver.getInstanceFactory()") final ClassFactory factory) {
    return new SObjectWithoutFields(receiver, factory);
  }

  @Fallback
  public final SAbstractObject fallback(final SClass receiver) {
    ClassFactory factory = receiver.getInstanceFactory();
    if (factory.hasSlots()) {
      if (factory.hasOnlyImmutableFields()) {
        return doClassWithOnlyImmutableFields(receiver, receiver.getInstanceFactory(), receiver.getInstanceFactory().getInstanceLayout());
      } else {
        return doClassWithFields(receiver, receiver.getInstanceFactory(), receiver.getInstanceFactory().getInstanceLayout());
      }
    } else {
      return doClassWithoutFields(receiver, receiver.getInstanceFactory());
    }
  }
}
