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

import com.oracle.truffle.api.frame.Frame;

public abstract class Arguments extends com.oracle.truffle.api.Arguments {

  protected final Object self;

  private Arguments(final Object self) {
    this.self = self;
  }

  public final Object getSelf() {
    return self;
  }

  public abstract Object getArgument(final int i);

  public static Arguments get(final Frame frame) {
    return frame.getArguments(Arguments.class);
  }

//  public static Arguments getUnary(final VirtualFrame frame) {
//    return frame.getArguments(UnaryArguments.class);
//  }
//
//  public static Arguments getBinary(final VirtualFrame frame) {
//    return frame.getArguments(BinaryArguments.class);
//  }
//
//  public static Arguments getTernary(final VirtualFrame frame) {
//    return frame.getArguments(TernaryArguments.class);
//  }
//
//  public static Arguments getKeyword(final VirtualFrame frame) {
//    return frame.getArguments(KeywordArguments.class);
//  }

  public static final class UnaryArguments extends Arguments {
    public UnaryArguments(final Object self) {
      super(self);
    }

    @Override
    public Object getArgument(final int i) {
      assert i == 0;
      return self;
    }
  }

  public static final class BinaryArguments extends Arguments {
    private final Object arg;
    public BinaryArguments(final Object self, final Object arg) {
      super(self);
      this.arg = arg;
    }

    @Override
    public Object getArgument(final int i) {
      if (i == 0) {
        return self;
      } else {
        assert i == 1;
        return arg;
      }
    }
  }

  public static final class TernaryArguments extends Arguments {
    private final Object arg1;
    private final Object arg2;

    public TernaryArguments(final Object self, final Object arg1, final Object arg2) {
      super(self);
      this.arg1 = arg1;
      this.arg2 = arg2;
    }

    @Override
    public Object getArgument(final int i) {
      if (i == 0) {
        return self;
      } else if (i == 1) {
        return arg1;
      } else {
        assert i == 2;
        return arg2;
      }
    }
  }

  public static final class KeywordArguments extends Arguments {
    private final Object[] arguments;

    public KeywordArguments(final Object self, final Object[] arguments) {
      super(self);
      this.arguments = arguments;
    }

    @Override
    public Object getArgument(final int i) {
      if (i == 0) {
        return self;
      }
      return arguments[i - 1];
    }
  }
}
