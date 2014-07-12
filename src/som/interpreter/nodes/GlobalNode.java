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

import som.interpreter.SArguments;
import som.interpreter.TruffleCompiler;
import som.vm.Nil;
import som.vm.Universe;
import som.vm.Universe.Association;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;


public abstract class GlobalNode extends ExpressionNode {

  protected final SSymbol  globalName;


  public GlobalNode(final SSymbol globalName, final SourceSection source) {
    super(source);
    this.globalName = globalName;
  }

  @Override
  public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }

  public static final class UninitializedGlobalReadNode extends GlobalNode {
    private final BranchProfile unknownGlobalNotFound;
    private final Universe universe;

    public UninitializedGlobalReadNode(final SSymbol globalName,
        final Universe universe, final SourceSection source) {
      super(globalName, source);
      unknownGlobalNotFound = new BranchProfile();
      this.universe = universe;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      TruffleCompiler.transferToInterpreterAndInvalidate("Uninitialized Global Node");

      // first let's check whether it is one of the well known globals
      switch (globalName.getString()) {
        case "true":
          return replace(new TrueGlobalNode(globalName, getSourceSection())).
              executeGeneric(frame);
        case "false":
          return replace(new FalseGlobalNode(globalName, getSourceSection())).
              executeGeneric(frame);
        case "nil":
          return replace(new NilGlobalNode(globalName, universe,
              getSourceSection())).
                executeGeneric(frame);
      }

      // Get the global from the universe
      Association assoc = universe.getGlobalsAssociation(globalName);
      if (assoc != null) {
        return replace(new CachedGlobalReadNode(globalName, assoc,
            getSourceSection())).executeGeneric(frame);
      } else {
        unknownGlobalNotFound.enter();
        // if it is not defined, we will send a error message to the current
        // receiver object
        Object self = SArguments.rcvr(frame);
        return SAbstractObject.sendUnknownGlobal(self, globalName, universe);
      }
    }
  }

  private static final class CachedGlobalReadNode extends GlobalNode {
    private final Association assoc;

    private CachedGlobalReadNode(final SSymbol globalName,
        final Association assoc, final SourceSection source) {
      super(globalName, source);
      this.assoc = assoc;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return assoc.getValue();
    }
  }

  private static final class TrueGlobalNode extends GlobalNode {
    public TrueGlobalNode(final SSymbol globalName, final SourceSection source) {
      super(globalName, source);
    }

    @Override
    public boolean executeBoolean(final VirtualFrame frame) {
      return true;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return executeBoolean(frame);
    }
  }

  private static final class FalseGlobalNode extends GlobalNode {
    public FalseGlobalNode(final SSymbol globalName,
        final SourceSection source) {
      super(globalName, source);
    }

    @Override
    public boolean executeBoolean(final VirtualFrame frame) {
      return false;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return executeBoolean(frame);
    }
  }

  private static final class NilGlobalNode extends GlobalNode {
    public NilGlobalNode(final SSymbol globalName, final Universe universe,
        final SourceSection source) {
      super(globalName, source);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return Nil.nilObject;
    }
  }
}
