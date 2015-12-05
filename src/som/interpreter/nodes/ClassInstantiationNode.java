package som.interpreter.nodes;

import som.compiler.MixinDefinition;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.constants.Classes;
import som.vm.constants.KernelObj;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;


public abstract class ClassInstantiationNode extends Node {

  private final MixinDefinition mixinDef;

  public ClassInstantiationNode(final MixinDefinition mixinDef) {
    this.mixinDef = mixinDef;
  }

  public abstract SClass execute(final SObjectWithClass outerObj,
      final Object superclassAndMixins);

  protected final ClassFactory createClassFactory(final Object superclassAndMixins) {
    return mixinDef.createClassFactory(superclassAndMixins, false, false, false);
  }

  protected boolean sameSuperAndMixins(final Object superclassAndMixins, final Object cached) {
    return MixinDefinition.sameSuperAndMixins(superclassAndMixins, cached);
  }

  @Specialization(guards = {"sameSuperAndMixins(superclassAndMixins, cachedSuperMixins)"})
  public SClass instantiateClass(final SObjectWithClass outerObj,
      final Object superclassAndMixins,
      @Cached("superclassAndMixins") final Object cachedSuperMixins,
      @Cached("createClassFactory(superclassAndMixins)") final ClassFactory factory) {
    return instantiate(outerObj, factory);
  }

  public static SClass instantiate(final SObjectWithClass outerObj,
      final ClassFactory factory) {
    SClass resultClass = new SClass(outerObj, Classes.metaclassClass);
    factory.getClassClassFactory().initializeClass(resultClass);

    SClass result = new SClass(outerObj, resultClass);
    factory.initializeClass(result);

    if (factory.isDeclaredAsValue() && factory.isTransferObject()) {
      // REM: cast is fine here, because we never return anyway
      return (SClass) KernelObj.signalException("signalTOCannotBeValues:", result);
    }

    if ((factory.isTransferObject() || factory.isDeclaredAsValue()) &&
        !outerObj.isValue()) {
      return (SClass) KernelObj.signalException("signalNotAValueWith:", result);
    }
    return result;
  }

  @Fallback
  public SClass instantiateClass(final SObjectWithClass outerObj,
      final Object superclassAndMixins) {
    return instantiateClass(outerObj, superclassAndMixins, null,
        mixinDef.createClassFactory(superclassAndMixins, false, false, false));
  }
}
