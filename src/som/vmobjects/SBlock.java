/**
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

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

import som.interop.SBlockInteropMessageResolutionForeign;
import som.vm.constants.Classes;


public final class SBlock extends SAbstractObject
    implements TruffleObject, SObjectWithContext {

  private final SInvokable        method;
  private final MaterializedFrame context;

  public SBlock(final SInvokable blockMethod, final MaterializedFrame context) {
    this.method = blockMethod;
    this.context = context;
  }

  public SInvokable getMethod() {
    return method;
  }

  @Override
  public MaterializedFrame getContext() {
    assert context != null;
    return context;
  }

  @Override
  public SClass getSOMClass() {
    return Classes.blockClass;
  }

  @Override
  public boolean isValue() {
    return false;
  }

  @Override
  public String toString() {
    return super.toString() + "[" + method.toString() + "]";
  }

  @Override
  public ForeignAccess getForeignAccess() {
    return SBlockInteropMessageResolutionForeign.ACCESS;
  }
}
