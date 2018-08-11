package ext;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import dep.SomeDependency;
import som.interpreter.nodes.nary.UnaryExpressionNode;


public abstract class ExtensionPrims {

  private static long cnt = 0;

  @GenerateNodeFactory
  @Primitive(primitive = "inc")
  public abstract static class IncPrim extends UnaryExpressionNode {
    @Specialization
    public static final Object inc(final Object o) {
      cnt += 1;
      return cnt;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "counter")
  public abstract static class CounterPrim extends UnaryExpressionNode {
    @Specialization
    public static final Object count(final Object o) {
      return cnt;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "valueFromClasspath")
  public abstract static class GetValuePrim extends UnaryExpressionNode {
    @Specialization
    public static final Object inc(final Object o) {
      return SomeDependency.getValue();
    }
  }
}
