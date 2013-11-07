package som.primitives;

import som.interpreter.nodes.messages.UnaryMonomorphicNode;
import som.primitives.arithmetic.ArithmeticPrim;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SInteger;
import som.vmobjects.SMethod;
import som.vmobjects.SObject;
import som.vmobjects.SString;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class IntegerPrims {


  public abstract static class RandomPrim extends UnaryMonomorphicNode {
    public RandomPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public RandomPrim(final RandomPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    @Specialization
    public SAbstractObject doSInteger(final SInteger receiver) {
      return universe.newInteger((int) (receiver.getEmbeddedInteger() * Math.random()));
    }
  }

  public abstract static class FromStringPrim extends ArithmeticPrim {
    public FromStringPrim(final SSymbol selector, final Universe universe, final SClass rcvrClass, final SMethod invokable) { super(selector, universe, rcvrClass, invokable); }
    public FromStringPrim(final FromStringPrim prim) { this(prim.selector, prim.universe, prim.rcvrClass, prim.invokable); }

    protected boolean receiverIsIntegerClass(final SObject receiver) {
      return receiver == universe.integerClass;
    }

    @Specialization(guards = "receiverIsIntegerClass")
    public SAbstractObject doSClass(final SClass receiver, final SString argument) {
      long result = Long.parseLong(argument.getEmbeddedString());
      return makeInt(result);
    }
  }
}
