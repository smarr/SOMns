package som.interpreter.nodes.dispatch;

import som.compiler.AccessModifier;
import som.compiler.ClassBuilder.ClassDefinitionId;
import som.interpreter.Types;
import som.interpreter.nodes.ISuperReadNode;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Super sends are special, they lead to a lexically defined receiver class.
 * So, it's always the cached receiver.
 */
public abstract class SuperDispatchNode extends AbstractDispatchNode {
  // TODO: remove the useless wrapper class

  public static SuperDispatchNode create(final SSymbol selector,
      final ISuperReadNode superNode) {
    CompilerAsserts.neverPartOfCompilation("SuperDispatchNode.create1");
    return new UninitializedDispatchNode(selector, superNode.getHolderClass(),
        superNode.isClassSide());
  }

  private static final class UninitializedDispatchNode extends SuperDispatchNode {
    private final SSymbol selector;
    private final ClassDefinitionId holderClass;
    private final boolean classSide;

    private UninitializedDispatchNode(final SSymbol selector,
        final ClassDefinitionId holderClass, final boolean classSide) {
      this.selector    = selector;
      this.holderClass = holderClass;
      this.classSide   = classSide;
    }

    private SClass getSuperClass(final SClass rcvrClass) {
      SClass cls = rcvrClass.getClassCorrespondingTo(holderClass);
      SClass superClass = cls.getSuperClass();

      if (classSide) {
        return superClass.getSOMClass();
      } else {
        return superClass;
      }
    }

    private AbstractDispatchNode specialize(final Object rcvr) {
      // TODO: misses the handling of chain length!!!

      CompilerAsserts.neverPartOfCompilation("SuperDispatchNode.create2");
      // TODO: integrate this with the normal specialization code, to reuse the DNU handling
      SClass rcvrClass = Types.getClassOf(rcvr);
      Dispatchable disp = getSuperClass(rcvrClass).lookupMessage(
          selector, AccessModifier.PROTECTED);

      if (disp == null) {
        throw new RuntimeException("Currently #dnu with super sent is not yet implemented. ");
      }

      UninitializedDispatchNode next = new UninitializedDispatchNode(selector,
          holderClass, classSide);

      // The reason that his is a checking dispatch is because the superclass
      // hierarchy is dynamic, and it is perfectly possible that super sends
      // bind in the same lexical location to different methods
      return replace(disp.getDispatchNode(rcvr, rcvrClass, next));
    }

    @Override
    public Object executeDispatch(
        final VirtualFrame frame, final Object[] arguments) {
      return specialize(arguments[0]).
          executeDispatch(frame, arguments);
    }

    @Override
    public String toString() {
      return "UninitSuper(" + selector.toString()
          + (classSide ? ", clsSide" : "") + ")";
    }
  }

  @Override
  public final int lengthOfDispatchChain() {
    return 1;
  }
}
