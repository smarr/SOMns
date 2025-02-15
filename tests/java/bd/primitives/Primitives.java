package bd.primitives;

import java.util.ArrayList;
import java.util.List;

import bd.testsetup.AbsNodeFactory;
import bd.testsetup.AddAbsNodeFactory;
import bd.testsetup.AddNodeFactory;
import bd.testsetup.AddWithSpecializerNodeFactory;
import bd.testsetup.ExprNode;
import bd.testsetup.LangContext;
import bd.testsetup.StringId;


public class Primitives extends PrimitiveLoader<LangContext, ExprNode, String> {
  protected Primitives() {
    super(new StringId());
    initialize();
  }

  @Override
  protected List<Specializer<LangContext, ExprNode, String>> getSpecializers() {
    List<Specializer<LangContext, ExprNode, String>> allSpecializers = new ArrayList<>();

    add(allSpecializers, AddNodeFactory.getInstance());
    add(allSpecializers, AddWithSpecializerNodeFactory.getInstance());
    add(allSpecializers, AbsNodeFactory.getInstance());
    add(allSpecializers, AddAbsNodeFactory.getInstance());

    return allSpecializers;
  }

  @Override
  protected void registerPrimitive(
      final Specializer<LangContext, ExprNode, String> specializer) {
    /* not needed for testing */
  }
}
