package som.interpreter.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;

import som.compiler.MixinDefinition;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import tools.snapshot.nodes.ObjectSerializationNodesFactory.UninitializedObjectSerializationNodeFactory;
import com.oracle.truffle.api.frame.VirtualFrame;


public abstract class InstantiationNode extends Node {

  private final MixinDefinition mixinDefinition;

  @Child protected ExceptionSignalingNode notAValue;
  @Child protected ExceptionSignalingNode cannotBeValues;

  protected InstantiationNode(final MixinDefinition mixinDef) {
    mixinDefinition = mixinDef;
    notAValue = insert(ExceptionSignalingNode.createNotAValueNode(
        mixinDefinition.getInitializerSourceSection()));
    cannotBeValues = insert(ExceptionSignalingNode.createNode(
        Symbols.TransferObjectsCannotBeValues,
        mixinDefinition.getInitializerSourceSection()));
  }

  protected final ClassFactory createClassFactory(final Object superclassAndMixins) {
    return mixinDefinition.createClassFactory(superclassAndMixins, false, false, false,
        UninitializedObjectSerializationNodeFactory.getInstance());
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
          final VirtualFrame frame, final SObjectWithClass outerObj, final ClassFactory factory,
          final SClass classObj, final ExceptionSignalingNode notAValue,
          final ExceptionSignalingNode cannotBeValue) {
    factory.initializeClass(classObj);

    if (factory.isDeclaredAsValue() && factory.isTransferObject()) {
      // REM: cast is fine here, because we never return anyway
      cannotBeValue.signal(frame, classObj);
    }

    if ((factory.isTransferObject() || factory.isDeclaredAsValue()) &&
        !outerObj.isValue()) {
      notAValue.signal(frame, classObj);
    }

    return classObj;
  }

  public abstract static class ClassInstantiationNode extends InstantiationNode {

    public ClassInstantiationNode(final MixinDefinition mixinDefinition) {
      super(mixinDefinition);
    }

    public abstract SClass execute(VirtualFrame frame, SObjectWithClass outerObj, Object superclassAndMixins);

    @Specialization(guards = {"sameSuperAndMixins(superclassAndMixins, cachedSuperMixins)"})
    public SClass instantiateClass(final VirtualFrame frame, final SObjectWithClass outerObj,
        final Object superclassAndMixins,
        @Cached("superclassAndMixins") final Object cachedSuperMixins,
        @Cached("createClassFactory(superclassAndMixins)") final ClassFactory factory) {
      return instantiate(frame, outerObj, factory, notAValue, cannotBeValues);
    }

    public static SClass instantiate(final VirtualFrame frame, final SObjectWithClass outerObj,
        final ClassFactory factory, final ExceptionSignalingNode notAValue,
        final ExceptionSignalingNode cannotBeValues) {
      SClass classObj = new SClass(outerObj, instantiateMetaclassClass(factory, outerObj));
      return signalExceptionsIfFaultFoundElseReturnClassObject(frame, outerObj, factory, classObj,
          notAValue, cannotBeValues);
    }

    @Specialization(replaces = "instantiateClass")
    public SClass instantiateClassWithNewClassFactory(final VirtualFrame frame, final SObjectWithClass outerObj,
        final Object superclassAndMixins) {
      return instantiateClass(frame, outerObj, superclassAndMixins, null,
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
      return signalExceptionsIfFaultFoundElseReturnClassObject(frame, outerObj, factory, classObj,
          inst.notAValue, inst.cannotBeValues);
    }

    @Specialization(replaces = "instantiateClass")
    public SClass instantiateClassWithNewClassFactory(final SObjectWithClass outerObj,
        final Object superclassAndMixins, final MaterializedFrame frame) {
      return instantiateClass(outerObj, superclassAndMixins, frame, null,
          createClassFactory(superclassAndMixins));
    }
  }
}
