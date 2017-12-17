package som.interpreter.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;

import som.compiler.MixinDefinition;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.constants.Classes;
import som.vm.constants.KernelObj;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;


public class InstantiationNode extends Node {

  private final MixinDefinition mixinDefinition;

  public InstantiationNode(final MixinDefinition mixinDef) {
    this.mixinDefinition = mixinDef;
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

  public static SClass singalExceptionsIfFaultFoundElseReturnClassObject(
      final SObjectWithClass outerObj,
      final ClassFactory factory, final SClass classObj) {
    factory.initializeClass(classObj);

    if (factory.isDeclaredAsValue() && factory.isTransferObject()) {
      // REM: cast is fine here, because we never return anyway
      return (SClass) KernelObj.signalExceptionWithClass("signalTOCannotBeValues:", classObj);
    }

    if ((factory.isTransferObject() || factory.isDeclaredAsValue()) &&
        !outerObj.isValue()) {
      return (SClass) KernelObj.signalExceptionWithClass("signalNotAValueWith:", classObj);
    }

    return classObj;
  }

  public abstract static class ClassInstantiationNode extends InstantiationNode {

    public ClassInstantiationNode(final MixinDefinition mixinDefinition) {
      super(mixinDefinition);
    }

    public abstract SClass execute(SObjectWithClass outerObj, Object superclassAndMixins);

    @Specialization(guards = {"sameSuperAndMixins(superclassAndMixins, cachedSuperMixins)"})
    public SClass instantiateClass(final SObjectWithClass outerObj,
        final Object superclassAndMixins,
        @Cached("superclassAndMixins") final Object cachedSuperMixins,
        @Cached("createClassFactory(superclassAndMixins)") final ClassFactory factory) {
      return instantiate(outerObj, factory);
    }

    public static SClass instantiate(final SObjectWithClass outerObj,
        final ClassFactory factory) {
      SClass classObj = new SClass(outerObj, instantiateMetaclassClass(factory, outerObj));
      return singalExceptionsIfFaultFoundElseReturnClassObject(outerObj, factory, classObj);
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
      return instantiate(outerObj, factory, frame);
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
        final ClassFactory factory, final MaterializedFrame frame) {
      SClass classObj =
          new SClass(outerObj, instantiateMetaclassClass(factory, outerObj), frame);
      return singalExceptionsIfFaultFoundElseReturnClassObject(outerObj, factory, classObj);
    }

    @Specialization(replaces = "instantiateClass")
    public SClass instantiateClassWithNewClassFactory(final SObjectWithClass outerObj,
        final Object superclassAndMixins, final MaterializedFrame frame) {
      return instantiateClass(outerObj, superclassAndMixins, frame, null,
          createClassFactory(superclassAndMixins));
    }
  }
}
