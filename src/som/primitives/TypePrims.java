package som.primitives;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.Types;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Symbols;
import som.vm.constants.Nil;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import som.vmobjects.SType;
import som.vmobjects.SType.BrandType;


public final class TypePrims {

  @GenerateNodeFactory
  @Primitive(primitive = "typeClass:")
  public abstract static class SetTypeClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SType.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "type:intersect:")
  @Primitive(selector = "intersect:", receiverType = SType.class)
  public abstract static class TypeIntersectPrim extends BinaryExpressionNode {
    // TODO: Add specialization for custom types (so don't only expect SType)

    @Specialization
    public Object performTypeCheckOnNil(final SType left, final SType right) {
      return new SType.IntersectionType(left, right);
    }

  }

  @GenerateNodeFactory
  @Primitive(primitive = "typeNewBrand:")
  public abstract static class CreateTypeBrandPrim extends UnaryExpressionNode {

    @Specialization
    public Object createBrand(final Object o) {
      return new SType.BrandType();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "brand:with:")
  public abstract static class BrandObjectWithBrandPrim extends BinaryExpressionNode {

    @Specialization
    public Object createBrand(final Object o, final BrandType brand) {
      brand.brand(o);
      return Nil.nilObject;
    }

  }

  @GenerateNodeFactory
  @Primitive(selector = "|", receiverType = SType.class)
  public abstract static class TypeVariantPrim extends BinaryExpressionNode {

    @Specialization
    public final Object doTypeVariant(final SType left, final SType right) {
      return new SType.VariantType(left, right);
    }

  }

  @GenerateNodeFactory
  @Primitive(primitive = "type:checkOrError:")
  public abstract static class TypeCheckPrim extends BinaryExpressionNode {

    protected void throwTypeError(final SType expected, final Object obj) {
      CompilerDirectives.transferToInterpreter();

      ExceptionSignalingNode exception = insert(
          ExceptionSignalingNode.createNode(Symbols.symbolFor("TypeError"), sourceSection));

      Object type = Types.getClassOf(obj).type;
      int line = sourceSection.getStartLine();
      int column = sourceSection.getStartColumn();
      // TODO: Get the real source of the type check
      // String[] parts = sourceSection.getSource().getURI().getPath().split("/");
      // String suffix = parts[parts.length - 1] + " [" + line + "," + column + "]";
      String suffix = "{UNKNOWN-FILE} [" + line + "," + column + "]";
      exception.signal(
          suffix + " \"" + obj + "\" is not a subtype of " + expected
              + ", because it has the type " + type);
    }

    protected SClass getClass(final Object obj) {
      return Types.getClassOf(obj);
    }

    protected boolean isNil(final SObjectWithoutFields obj) {
      return obj == Nil.nilObject;
    }

    @Specialization(guards = {"isNil(obj)"})
    public Object performTypeCheckOnNil(final SType expected, final SObjectWithoutFields obj) {
      return Nil.nilObject;
    }
  }
}
