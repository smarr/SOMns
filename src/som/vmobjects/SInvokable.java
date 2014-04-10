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

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.Invokable;
import som.vm.Universe;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;

public abstract class SInvokable extends SAbstractObject {

  public SInvokable(final SSymbol signature, final Invokable invokable) {
    this.signature = signature;

    this.invokable   = invokable;
    this.callTarget  = invokable.createCallTarget();
  }

  public static class SMethod extends SInvokable {
    public SMethod(final SSymbol signature, final Invokable invokable) {
      super(signature, invokable);
    }

    @Override
    public SClass getSOMClass(final Universe universe) {
      return universe.methodClass;
    }
  }

  public static class SPrimitive extends SInvokable {
    public SPrimitive(final SSymbol signature, final Invokable invokable) {
      super(signature, invokable);
    }

    @Override
    public SClass getSOMClass(final Universe universe) {
      return universe.primitiveClass;
    }
  }

  public RootCallTarget getCallTarget() {
    return callTarget;
  }

  public Invokable getInvokable() {
    return invokable;
  }

  public SSymbol getSignature() {
    return signature;
  }

  public SClass getHolder() {
    return holder;
  }

  public void setHolder(final SClass value) {
    transferToInterpreterAndInvalidate("SMethod.setHolder");
    holder = value;
  }

  public int getNumberOfArguments() {
    // Get the number of arguments of this method
    return getSignature().getNumberOfSignatureArguments();
  }

  public Object invoke(final Object self) {
    return callTarget.call(new Object[] {self});
  }

  public Object invoke(final Object self, final Object arg) {
    return callTarget.call(new Object[] {self, arg});
  }

  public Object invoke(final Object self, final Object arg1, final Object arg2) {
    return callTarget.call(new Object[] {self, arg1, arg2});
  }

  public Object invoke(final Object[] args) {
    return callTarget.call(args);
  }

  @Override
  public String toString() {
    // TODO: fixme: remove special case if possible, I think it indicates a bug
    if (holder == null) {
      return "Method(nil>>" + getSignature().toString() + ")";
    }

    return "Method(" + getHolder().getName().getString() + ">>" + getSignature().toString() + ")";
  }

  // Private variable holding Truffle runtime information
  private final Invokable              invokable;
  private final RootCallTarget         callTarget;
  private final SSymbol                signature;
  @CompilationFinal private SClass     holder;
}
