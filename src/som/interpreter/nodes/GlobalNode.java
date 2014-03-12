/**
 * Copyright (c) 2013 Stefan Marr, stefan.marr@vub.ac.be
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package som.interpreter.nodes;

import static com.oracle.truffle.api.nodes.NodeInfo.Kind.SPECIALIZED;
import som.interpreter.SArguments;
import som.interpreter.TruffleCompiler;
import som.vm.Universe;
import som.vm.Universe.Association;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;
import com.oracle.truffle.api.utilities.BranchProfile;


public abstract class GlobalNode extends ExpressionNode
    implements PreevaluatedExpression {

  protected final SSymbol  globalName;
  protected final Universe universe;

  public GlobalNode(final SSymbol globalName, final Universe universe) {
    this.globalName = globalName;
    this.universe   = universe;
  }

  @Override
  public final Object executePreEvaluated(final VirtualFrame frame,
      final Object receiver, final Object[] arguments) {
    return executeGeneric(frame);
  }

  public static final class UninitializedGlobalReadNode extends GlobalNode {
    private final BranchProfile unknownGlobalNotFound;

    public UninitializedGlobalReadNode(final SSymbol globalName, final Universe universe) {
      super(globalName, universe);
      unknownGlobalNotFound = new BranchProfile();
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Uninitialized Global Node");
      // Get the global from the universe
      Association assoc = universe.getGlobalsAssociation(globalName);
      if (assoc != null) {
        return replace(new CachedGlobalReadNode(globalName, universe, assoc)).executeGeneric(frame);
      } else {
        unknownGlobalNotFound.enter();
        // if it is not defined, we will send a error message to the current
        // receiver object
        Object self = SArguments.getReceiverFromFrame(frame);
        return SAbstractObject.sendUnknownGlobal(self, globalName, universe,
            frame.pack());
      }
    }

    @Override
    public void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }

    @Override
    public Kind getKind() {
        return Kind.UNINITIALIZED;
    }
  }

  private static final class CachedGlobalReadNode extends GlobalNode {
    private final Association assoc;

    private CachedGlobalReadNode(final SSymbol globalName,
        final Universe universe, final Association assoc) {
      super(globalName, universe);
      this.assoc = assoc;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return assoc.value;
    }

    @Override
    public void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }

    @Override
    public Kind getKind() {
        return SPECIALIZED;
    }
  }
}
