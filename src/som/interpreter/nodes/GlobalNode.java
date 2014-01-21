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

import som.interpreter.Arguments;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.utilities.BranchProfile;


public abstract class GlobalNode extends ExpressionNode {

  protected final SSymbol  globalName;
  protected final Universe universe;

  public GlobalNode(final SSymbol globalName, final Universe universe) {
    this.globalName = globalName;
    this.universe   = universe;
  }

  public static class UninitializedGlobalReadNode extends GlobalNode {
    public UninitializedGlobalReadNode(final SSymbol globalName, final Universe universe) {
      super(globalName, universe);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      // Get the global from the universe
      SAbstractObject globalValue = universe.getGlobal(globalName);
      if (globalValue != null) {
        return replace(new CachedGlobalReadNode(globalName, universe, globalValue, universe.getCurrentGlobalsUnchangedAssumption())).executeGeneric(frame);
      } else {
        return replace(new GenericGlobalReadNode(globalName, universe)).executeGeneric(frame);
      }
    }
  }

  private static final class CachedGlobalReadNode extends GlobalNode {
    private final SAbstractObject globalValue;
    private final Assumption      globalsUnchanged;

    private CachedGlobalReadNode(final SSymbol globalName, final Universe universe,
        final SAbstractObject globalValue, final Assumption globalsUnchanged) {
      super(globalName, universe);
      this.globalValue      = globalValue;
      this.globalsUnchanged = globalsUnchanged;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      try {
        globalsUnchanged.check();
        return globalValue;
      } catch (InvalidAssumptionException e) {
        return replace(new GenericGlobalReadNode(globalName, universe)).executeGeneric(frame);
      }
    }
  }

  private static final class GenericGlobalReadNode extends GlobalNode {
    private final BranchProfile unknownGlobalNotFound;

    private GenericGlobalReadNode(final SSymbol globalName, final Universe universe) {
      super(globalName, universe);
      unknownGlobalNotFound = new BranchProfile();
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      // Get the global from the universe
      SAbstractObject global = universe.getGlobal(globalName);

      if (global != null) {
        return global;
      } else {
        unknownGlobalNotFound.enter();
        // if it is not defined, we will send a error message to the current
        // receiver object
        Object self = Arguments.get(frame).getSelf();
        return SAbstractObject.sendUnknownGlobal(self, globalName, universe,
            frame.pack());
      }
    }
  }
}
