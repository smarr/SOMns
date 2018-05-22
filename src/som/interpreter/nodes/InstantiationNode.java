package som.interpreter.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;

import som.compiler.MixinDefinition;
import som.interpreter.SomLanguage;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.constants.Classes;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;


public class InstantiationNode extends Node {

  private final MixinDefinition           mixinDefinition;
  @Child protected ExceptionSignalingNode notAValueThrower;
  @Child protected ExceptionSignalingNode cannotBeValuesThrower;

  public InstantiationNode(final MixinDefinition mixinDef) {
    this.mixinDefinition = mixinDef;
    notAValueThrower = ExceptionSignalingNode.createNotAValueExceptionSignalingNode(
        mixinDef.getSourceSection());
    cannotBeValuesThrower =
        ExceptionSignalingNode.createKernelSignalWithExceptionNode(
            "TransferObjectsCannotBeValues", mixinDef.getSourceSection());
  }

  protected final ClassFactory createClassFactory(final Object superclassAndMixins) {
    return mixinDefinition.createClassFactory(superclassAndMixins, false, false, false);
  }

  protected boolean sameSuperAndMixins(final Object superclassAndMixins, final Object cached) {
    return MixinDefinition.sameSuperAndMixins(superclassAndMixins, cached);
  }

  public static SClass instantiateMetaclassClass(final ClassFactory factory,
      final SObjectWithClass outerObj) {
    SClass metaclassClass = new SClass(outerObj, Classes.metaclassClass);
    factory.getClassClassFactory().initializeClass(metaclassClass);
    return metaclassClass;
  }

  public static SClass signalExceptionsIfFaultFoundElseReturnClassObject(
      final SObjectWithClass outerObj,
      final ClassFactory factory, final SClass classObj,
      final ExceptionSignalingNode notAValue, final ExceptionSignalingNode cannotBeValue) {
    factory.initializeClass(classObj);

    if (factory.isDeclaredAsValue() && factory.isTransferObject()) {
      // REM: cast is fine here, because we never return anyway
      cannotBeValue.execute(classObj);
    }

    if ((factory.isTransferObject() || factory.isDeclaredAsValue()) &&
        !outerObj.isValue()) {
      notAValue.execute(classObj);
    }

    return classObj;
  }

  public abstract static class ClassInstantiationNode extends InstantiationNode {

    // The parser can instantiate classes, those nodes are used to throw the exception in such
    // case.

    @Child protected ExceptionSignalingNode notAValueThrower;

    @Child protected ExceptionSignalingNode cannotBeValuesThrower;

    public ClassInstantiationNode(final MixinDefinition mixinDefinition) {
      super(mixinDefinition);
    }

    public abstract SClass execute(SObjectWithClass outerObj, Object superclassAndMixins);

    @Specialization(guards = {"sameSuperAndMixins(superclassAndMixins, cachedSuperMixins)"})
    public SClass instantiateClass(final SObjectWithClass outerObj,
        final Object superclassAndMixins,
        @Cached("superclassAndMixins") final Object cachedSuperMixins,
        @Cached("createClassFactory(superclassAndMixins)") final ClassFactory factory) {

      if (notAValueThrower == null) {
        notAValueThrower = insert(ExceptionSignalingNode.createNotAValueExceptionSignalingNode(
            SomLanguage.getSyntheticSource("", "ClassInstantiation instantiate")
                       .createSection(1)));
        cannotBeValuesThrower =
            insert(ExceptionSignalingNode.createKernelSignalWithExceptionNode(
                "TransferObjectsCannotBeValues",
                SomLanguage.getSyntheticSource("", "ClassInstantiation instantiate")
                           .createSection(1)));
      }

      return instantiate(outerObj, factory, notAValueThrower, cannotBeValuesThrower);
    }

    public static SClass instantiate(final SObjectWithClass outerObj,
        final ClassFactory factory, final ExceptionSignalingNode floatingNotAValueThrower,
        final ExceptionSignalingNode floatingCannotBeValuesThrower) {
      SClass classObj = new SClass(outerObj, instantiateMetaclassClass(factory, outerObj));
      return signalExceptionsIfFaultFoundElseReturnClassObject(outerObj, factory, classObj,
          floatingNotAValueThrower, floatingCannotBeValuesThrower);
    }

    @Specialization(replaces = "instantiateClass")
    public SClass instantiateClassWithNewClassFactory(final SObjectWithClass outerObj,
        final Object superclassAndMixins) {
      return instantiateClass(outerObj, superclassAndMixins, null,
          createClassFactory(superclassAndMixins));
    }
  }

  public abstract static class ObjectLiteralInstantiationNode extends InstantiationNode {

    public ObjectLiteralInstantiationNode(final MixinDefinition mixinDefinition) {
      super(mixinDefinition);
    }

    public abstract SClass execute(SObjectWithClass outerObj, Object superclassAndMixins,
        MaterializedFrame frame);

    @Specialization(guards = {"sameSuperAndMixins(superclassAndMixins, cachedSuperMixins)"})
    public SClass instantiateClass(final SObjectWithClass outerObj,
        final Object superclassAndMixins, final MaterializedFrame frame,
        @Cached("superclassAndMixins") final Object cachedSuperMixins,
        @Cached("createClassFactory(superclassAndMixins)") final ClassFactory factory) {
      return instantiate(outerObj, factory, frame, this);
    }

    /**
     * The method is used to instantiate an object literal. The object literal's class
     * holds the current activation. The object literal's class's enclosing class is the
     * class of the outer object.
     *
     * @param frame, the current activation
     * @return an object instantiated from the newly created class.
     */
    private static SClass instantiate(final SObjectWithClass outerObj,
        final ClassFactory factory, final MaterializedFrame frame,
        final InstantiationNode inst) {
      SClass classObj =
          new SClass(outerObj, instantiateMetaclassClass(factory, outerObj), frame);
      return signalExceptionsIfFaultFoundElseReturnClassObject(outerObj, factory, classObj,
          inst.notAValueThrower, inst.cannotBeValuesThrower);
    }

    @Specialization(replaces = "instantiateClass")
    public SClass instantiateClassWithNewClassFactory(final SObjectWithClass outerObj,
        final Object superclassAndMixins, final MaterializedFrame frame) {
      return instantiateClass(outerObj, superclassAndMixins, frame, null,
          createClassFactory(superclassAndMixins));
    }
  }
}
