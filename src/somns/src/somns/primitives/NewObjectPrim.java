package somns.primitives;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.Tag;

import somns.compiler.MixinBuilder.MixinDefinitionId;
import somns.interpreter.nodes.ISpecialSend;
import somns.interpreter.nodes.nary.UnaryExpressionNode;
import somns.interpreter.objectstorage.ClassFactory;
import somns.interpreter.objectstorage.ObjectLayout;
import somns.vm.VmSettings;
import somns.vmobjects.SAbstractObject;
import somns.vmobjects.SClass;
import somns.vmobjects.SObject.SImmutableObject;
import somns.vmobjects.SObject.SMutableObject;
import somns.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.dym.Tags.NewObject;


// This isn't a primitive anymore, because we do all this in the magic of the
//  primary factory methods, which are generated in assemblePrimaryFactoryMethod()
// TODO: figure out where we need to do that, or whether we could actually do it
// in the language
// @GenerateNodeFactory
//    @Primitive("instantiate:")
public abstract class NewObjectPrim extends UnaryExpressionNode implements ISpecialSend {
  protected static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;

  private final MixinDefinitionId mixinId;

  public NewObjectPrim(final MixinDefinitionId mixinId) {
    this.mixinId = mixinId;
  }

  @Override
  public MixinDefinitionId getEnclosingMixinId() {
    return mixinId;
  }

  @Override
  protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
    if (tag == NewObject.class) {
      return true;
    } else {
      return super.hasTagIgnoringEagerness(tag);
    }
  }

  @Override
  public boolean isSuperSend() {
    return false;
  }

  @Specialization(guards = {
      "receiver.getInstanceFactory() == factory",
      "factory.hasSlots()",
      "factory.hasOnlyImmutableFields()",
      "receiver.getInstanceFactory().getInstanceLayout() == layout"},
      limit = "INLINE_CACHE_SIZE")
  public final SAbstractObject doClassWithOnlyImmutableFields(final SClass receiver,
      @Cached("receiver.getInstanceFactory()") final ClassFactory factory,
      @Cached("receiver.getInstanceFactory().getInstanceLayout()") final ObjectLayout layout) {
    return new SImmutableObject(receiver, factory, layout);
  }

  @Specialization(guards = {
      "receiver.getInstanceFactory() == factory",
      "factory.hasSlots()",
      "!factory.hasOnlyImmutableFields()",
      "receiver.getInstanceFactory().getInstanceLayout() == layout"},
      limit = "INLINE_CACHE_SIZE")
  public final SAbstractObject doClassWithFields(
      final SClass receiver,
      @Cached("receiver.getInstanceFactory()") final ClassFactory factory,
      @Cached("factory.getInstanceLayout()") final ObjectLayout layout) {
    return new SMutableObject(receiver, factory, layout);
  }

  @Specialization(guards = {
      "receiver.getInstanceFactory() == factory",
      "!factory.hasSlots()"},
      limit = "INLINE_CACHE_SIZE")
  public final SAbstractObject doClassWithoutFields(final SClass receiver,
      @Cached("receiver.getInstanceFactory()") final ClassFactory factory) {
    return new SObjectWithoutFields(receiver, factory);
  }

  @Specialization(replaces = {"doClassWithOnlyImmutableFields", "doClassWithFields",
      "doClassWithoutFields"})
  @TruffleBoundary
  public final SAbstractObject fallback(final Object rcvr) {
    SClass receiver = (SClass) rcvr;
    ClassFactory factory = receiver.getInstanceFactory();
    if (factory.hasSlots()) {
      if (factory.hasOnlyImmutableFields()) {
        return doClassWithOnlyImmutableFields(receiver, receiver.getInstanceFactory(),
            receiver.getInstanceFactory().getInstanceLayout());
      } else {
        return doClassWithFields(receiver, receiver.getInstanceFactory(),
            receiver.getInstanceFactory().getInstanceLayout());
      }
    } else {
      return doClassWithoutFields(receiver, receiver.getInstanceFactory());
    }
  }
}
