/**
 * Copyright (c) 2013 Stefan Marr,   stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
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

package som.vmobjects;

import static som.interpreter.SArguments.createSArguments;
import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.AbstractInvokable;
import som.vm.Universe;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;

public abstract class SInvokable extends SAbstractObject {

  public SInvokable(final SSymbol signature, final AbstractInvokable invokable,
      final boolean isUnenforced) {
    this.signature    = signature;
    this.isUnenforced = isUnenforced;

    this.invokable   = invokable;
    this.callTarget  = invokable.createCallTarget();
  }

  public static final class SMethod extends SInvokable {
    private final SMethod[] embeddedBlocks;

    public SMethod(final SSymbol signature, final AbstractInvokable invokable,
        final boolean isUnenforced, final SMethod[] embeddedBlocks) {
      super(signature, invokable, isUnenforced);
      this.embeddedBlocks = embeddedBlocks;
    }

    @Override
    public void setHolder(final SClass value) {
      super.setHolder(value);
      for (SMethod m : embeddedBlocks) {
        m.setHolder(value);
      }
    }

    @Override
    public SClass getSOMClass(final Universe universe) {
      return universe.methodClass;
    }
  }

  public static final class SPrimitive extends SInvokable {
    public SPrimitive(final SSymbol signature,
        final AbstractInvokable invokable, final boolean isUnenforced) {
      super(signature, invokable, isUnenforced);
    }

    @Override
    public SClass getSOMClass(final Universe universe) {
      return universe.primitiveClass;
    }
  }

  public final RootCallTarget getCallTarget() {
    return callTarget;
  }

  public final AbstractInvokable getInvokable() {
    return invokable;
  }

  public final SSymbol getSignature() {
    return signature;
  }

  public final SClass getHolder() {
    return holder;
  }

  @Override
  public final SObject getDomain() {
    CompilerAsserts.neverPartOfCompilation("SInvokable.getDomain()");
    return Universe.current().standardDomain;
  }

  public void setHolder(final SClass value) {
    transferToInterpreterAndInvalidate("SMethod.setHolder");
    holder = value;
  }

  public final int getNumberOfArguments() {
    // Get the number of arguments of this method
    return getSignature().getNumberOfSignatureArguments();
  }

  public final Object invokeWithSArguments(final Object[] arguments) {
    return callTarget.call(arguments);
  }

  public final Object invoke(final SObject domain, final boolean enforced, final Object... arguments) {
    return callTarget.call(createSArguments(domain, enforced, arguments));
  }

  public boolean isUnenforced() {
    return isUnenforced;
  }

  @Override
  public final String toString() {
    // TODO: fixme: remove special case if possible, I think it indicates a bug
    if (holder == null) {
      return "Method(nil>>" + getSignature().toString() + ")";
    }

    return "Method(" + getHolder().getName().getString() + ">>" + getSignature().toString() + ")";
  }

  // Private variable holding Truffle runtime information
  private final AbstractInvokable      invokable;
  private final RootCallTarget         callTarget;
  private final SSymbol                signature;
  private final boolean                isUnenforced;
  @CompilationFinal private SClass     holder;
}
