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

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.SArguments;
import som.interpreter.nodes.nary.ExprWithTagsNode;
import som.vmobjects.SObjectWithContext;


public abstract class ContextualNode extends ExprWithTagsNode {

  protected final int contextLevel;
  private final ValueProfile frameType;

  public ContextualNode(final int contextLevel, final SourceSection source) {
    super(source);
    this.contextLevel = contextLevel;
    this.frameType = ValueProfile.createClassProfile();
  }

  public final int getContextLevel() {
    return contextLevel;
  }

  @ExplodeLoop
  protected final MaterializedFrame determineContext(final VirtualFrame frame) {
    SObjectWithContext contextualSelf = (SObjectWithContext) SArguments.rcvr(frame);
    int i = contextLevel - 1;

    while (i > 0) {
      contextualSelf = (SObjectWithContext) contextualSelf.getContextualSelf();
      i--;
    }

    // Graal needs help here to see that this is always a MaterializedFrame
    // so, we record explicitly a class profile
    return frameType.profile(contextualSelf.getContext());
  }
}
