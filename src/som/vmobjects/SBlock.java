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

import som.interpreter.SArguments;

import com.oracle.truffle.api.frame.MaterializedFrame;

public final class SBlock extends SAbstractObject {

  private final SInvokable        method;
  private final MaterializedFrame context;
  private final SClass            blockClass;

  public SBlock(final SInvokable blockMethod, final MaterializedFrame context,
      final SClass blockClass) {
    this.method     = blockMethod;
    this.context    = context;
    this.blockClass = blockClass;
  }

  public SInvokable getMethod() {
    return method;
  }

  public MaterializedFrame getContext() {
    assert context != null;
    return context;
  }

  public Object getOuterSelf() {
    return SArguments.rcvr(getContext());
  }

  @Override
  public SClass getSOMClass() {
    return blockClass;
  }

  @Override
  public boolean isValue() {
    return false;
  }

  @Override
  public String toString() {
    return super.toString() + "[" + method.toString() + "]";
  }
}
