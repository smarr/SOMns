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
package som.interpreter;

import som.vmobjects.SAbstractObject;

import com.oracle.truffle.api.frame.VirtualFrame;

public final class Arguments extends com.oracle.truffle.api.Arguments {

  private final SAbstractObject   self;
  private final SAbstractObject[] arguments;

  public Arguments(final SAbstractObject self, final SAbstractObject[] arguments) {
    this.self      = self;
    this.arguments = arguments;
  }

  public SAbstractObject getSelf() {
    return self;
  }

  public SAbstractObject getArgument(final int i) {
    return arguments[i];
  }

  public static Arguments get(final VirtualFrame frame) {
    return frame.getArguments(Arguments.class);
  }
}
