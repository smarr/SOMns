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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ValueProfile;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SObjectWithContext;


public abstract class ContextualNode extends ExprWithTagsNode {

  protected final int        contextLevel;
  private final ValueProfile frameType;
  private final ValueProfile outerType;

  @CompilationFinal DetermineContext determineContext;

  public ContextualNode(final int contextLevel) {
    this.contextLevel = contextLevel;
    this.frameType = ValueProfile.createClassProfile();
    this.outerType = ValueProfile.createClassProfile();
  }

  public final int getContextLevel() {
    return contextLevel;
  }

  private DetermineContext buildChain(final SObjectWithContext self, final int i) {
    if (i > 0) {
      DetermineContext outer = buildChain(self.getOuterSelf(), i - 1);
      if (self instanceof SBlock) {
        return new BlockContext(outer);
      } else if (self instanceof SClass) {
        return new ClassContext(outer);
      } else {
        assert self instanceof SObjectWithClass;
        return new ObjectContext(outer);
      }
    } else {
      return null;
    }
  }

  @ExplodeLoop
  protected final MaterializedFrame determineContext(final Frame frame) {
    SObjectWithContext self = (SObjectWithContext) SArguments.rcvr(frame);

    int i = contextLevel - 1;

    if (i > 0) {
      boolean doBuildChain = determineContext == null;
      if (doBuildChain) {
        determineContext = buildChain(self, i);
      }

      self = determineContext.getOuterSelf(self);
    }

    // Graal needs help here to see that this is always a MaterializedFrame
    // so, we record explicitly a class profile
    return frameType.profile(outerType.profile(self).getContext());
  }

  /**
   * A chain of objects that only injects static type information with casts.
   * The types fixed by the lexical structure, and important to enable Graal to
   * avoid virtual calls.
   */
  public abstract static class DetermineContext {
    protected final DetermineContext next;

    protected DetermineContext(final DetermineContext next) {
      this.next = next;
    }

    protected abstract SObjectWithContext getOuterSelf(SObjectWithContext obj);

    protected final SObjectWithContext getOuter(final SObjectWithContext obj) {
      SObjectWithContext outer = obj.getOuterSelf();
      if (next != null) {
        return next.getOuterSelf(outer);
      } else {
        return outer;
      }
    }
  }

  public static final class BlockContext extends DetermineContext {

    public BlockContext(final DetermineContext next) {
      super(next);
    }

    @Override
    protected SObjectWithContext getOuterSelf(final SObjectWithContext obj) {
      SBlock block = (SBlock) obj;
      return getOuter(block);
    }
  }

  public static final class ObjectContext extends DetermineContext {

    public ObjectContext(final DetermineContext next) {
      super(next);
    }

    @Override
    protected SObjectWithContext getOuterSelf(final SObjectWithContext obj) {
      SObjectWithClass objWithClass = (SObjectWithClass) obj;
      return getOuter(objWithClass);
    }
  }

  public static final class ClassContext extends DetermineContext {

    public ClassContext(final DetermineContext next) {
      super(next);
    }

    @Override
    protected SObjectWithContext getOuterSelf(final SObjectWithContext obj) {
      SClass clazz = (SClass) obj;
      return getOuter(clazz);
    }
  }
}
