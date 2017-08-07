package som.interop;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

import som.interpreter.nodes.ExpressionNode;
import som.vm.constants.Nil;
import som.vmobjects.SAbstractObject;


public abstract class ValueConversion {

  @NodeChild(value = "obj", type = ExpressionNode.class)
  public abstract static class ToSomConversion extends Node {

    public abstract Object execute(VirtualFrame frame);

    public abstract Object executeEvaluated(Object obj);

    @Specialization
    public static boolean doInt(final boolean obj) {
      return obj;
    }

    @Specialization
    public static long doInt(final byte obj) {
      return obj;
    }

    @Specialization
    public static long doInt(final short obj) {
      return obj;
    }

    @Specialization
    public static long doInt(final int obj) {
      return obj;
    }

    @Specialization
    public static long doInt(final long obj) {
      return obj;
    }

    @Specialization
    public static double doDouble(final float obj) {
      return obj;
    }

    @Specialization
    public static double doDouble(final double obj) {
      return obj;
    }

    @Specialization
    public static String doString(final char obj) {
      return String.valueOf(obj);
    }

    @Specialization
    public static String doString(final String obj) {
      return obj;
    }

    @Specialization
    public static SAbstractObject doString(final SAbstractObject obj) {
      return obj;
    }

    @Specialization
    public static TruffleObject doString(final TruffleObject obj) {
      return obj;
    }

    @Specialization(guards = "obj == null")
    public static TruffleObject doNull(final Object obj) {
      return Nil.nilObject;
    }
  }

  @ExplodeLoop
  public static Object[] convertToArgArray(final ToSomConversion convert,
      final SAbstractObject rcvr, final Object[] args) {
    Object[] arguments = new Object[args.length + 1];
    arguments[0] = convert.executeEvaluated(rcvr);

    for (int i = 0; i < args.length; i++) {
      arguments[i + 1] = convert.executeEvaluated(args[i]);
    }
    return arguments;
  }

  @ExplodeLoop
  public static Object[] convertToArgArray(final ToSomConversion convert,
      final Object[] args) {
    Object[] arguments = new Object[args.length + 1];
    for (int i = 0; i < args.length; i++) {
      arguments[i] = convert.executeEvaluated(args[i]);
    }
    return arguments;
  }
}
