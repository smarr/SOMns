package som.interpreter.nodes;

import som.compiler.MixinDefinition;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.constants.Classes;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;


public abstract class ClassInstantiationNode extends Node {

  private final MixinDefinition mixinDef;

  public ClassInstantiationNode(final MixinDefinition mixinDef) {
    this.mixinDef = mixinDef;
  }

  public abstract SClass execute(final SObjectWithClass outerObj,
      final Object superclassAndMixins);

  protected final ClassFactory createClassFactory(final Object superclassAndMixins) {
    return mixinDef.createClassFactory(superclassAndMixins, false);
  }

  // TODO: do we need to specialize this guard?
  @ExplodeLoop
  protected final boolean sameSuperAndMixins(final Object superclassAndMixins, final Object cached) {
    if (!(cached instanceof Object[])) {
      assert cached instanceof SClass;
      assert superclassAndMixins instanceof SClass;

      // TODO: identity comparison? is that stable enough? otherwise, need also to make sure isValue is the same, I think
      return cached == superclassAndMixins
          || ((SClass) cached).getInstanceFactory() == ((SClass) superclassAndMixins).getInstanceFactory();
    }

    if (!(superclassAndMixins instanceof Object[])) {
      return false;
    }

    Object[] supMixArr = (Object[]) superclassAndMixins;
    Object[] cachedArr = (Object[]) cached;

    assert supMixArr.length == cachedArr.length; // should be based on lexical info and be compilation constant
    CompilerAsserts.compilationConstant(cachedArr.length);

    for (int i = 0; i < cachedArr.length; i++) {
      // TODO: is this really correct?
      //    -> does i == 0 need also to check isValue?
      if (cachedArr[i] != supMixArr[i] &&
          ((SClass) cachedArr[i]).getInstanceFactory() == ((SClass) supMixArr[i]).getInstanceFactory()) {
        return false;
      }
    }
    return true;
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
    return result;
  }

  @Fallback
  public SClass instantiateClass(final SObjectWithClass outerObj,
      final Object superclassAndMixins) {
    return instantiateClass(outerObj, superclassAndMixins, null, mixinDef.createClassFactory(superclassAndMixins, false));
  }
}
