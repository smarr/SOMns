package som.interpreter.nodes.enforced;

import som.interpreter.SArguments;
import som.interpreter.nodes.ExpressionNode;
import som.vm.Universe;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class EnforcedGlobalReadNode extends ExpressionNode {

  private final SSymbol globalName;
  private final SSymbol intercessionHandler;

  public EnforcedGlobalReadNode(final SSymbol globalName, final SourceSection source) {
    super(source, true);
    this.globalName = globalName;
    intercessionHandler = Universe.current().symbolFor("readGlobal:for:");
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    CompilerAsserts.neverPartOfCompilation();
    SObject currentDomain = SArguments.domain(frame);

    // reading globals is based on the current execution context, not the
    // receiver. thus, the difference with all other intercession handlers is
    // on purpose.
    SInvokable handler = currentDomain.getSOMClass(null).lookupInvokable(intercessionHandler);
    Object rcvr = SArguments.rcvr(frame);
    return handler.invoke(currentDomain, false, currentDomain, globalName, rcvr);
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }
}
