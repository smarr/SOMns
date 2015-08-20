package som.interpreter.nodes;

import som.compiler.MixinDefinition;
import som.interpreter.objectstorage.ClassFactory;
import som.vm.constants.Classes;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithoutFields;

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

  public abstract SClass execute(final SObjectWithoutFields outerObj,
      final Object superclassAndMixins);

  protected final ClassFactory createClassFactory(final Object superclassAndMixins) {
    return mixinDef.createClassFactory(superclassAndMixins, false);
  }

  @ExplodeLoop
  protected final boolean sameSuperAndMixins(final Object superclassAndMixins, final Object cached) {
    if (!(cached instanceof Object[])) {
      // TODO: identity comparison? is that stable enough? otherwise, need also to make sure isValue is the same, I think
      return cached == superclassAndMixins;
    }

    if (!(superclassAndMixins instanceof Object[])) {
      return false;
    }

    Object[] supMixArr = (Object[]) superclassAndMixins;
    Object[] cachedArr = (Object[]) cached;

    assert supMixArr.length == cachedArr.length; // should be based on lexical info and be compilation constant
    CompilerAsserts.compilationConstant(cachedArr.length);

    for (int i = 0; i < cachedArr.length; i++) {
      // TODO: is this really correct? I think, we are comparing here SClass identities
      //       which might not be as stable as we hope, perhaps better to check their
      //       respective class factories? !!! TODO!!! XXX
      //    -> in that case, i == 0 needs also to check isValue, I think!
      if (cachedArr[i] != supMixArr[i]) {
        return false;
      }
    }
    return true;
  }

  @Specialization(guards = {"sameSuperAndMixins(superclassAndMixins, cachedSuperMixins)"})
  public SClass instantiateClass(final SObjectWithoutFields outerObj,
      final Object superclassAndMixins,
      @Cached("superclassAndMixins") final Object cachedSuperMixins,
      @Cached("createClassFactory(superclassAndMixins)") final ClassFactory factory) {
    SClass resultClass = new SClass(outerObj, Classes.metaclassClass);
    SClass result = new SClass(outerObj, resultClass);
    factory.initializeClass(result);
    return result;
  }

  @Fallback
  public SClass instantiateClass(final SObjectWithoutFields outerObj,
      final Object superclassAndMixins) {
    return instantiateClass(outerObj, superclassAndMixins, null, mixinDef.createClassFactory(superclassAndMixins, false));
  }
}
