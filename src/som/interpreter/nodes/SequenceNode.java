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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.nodes.nary.ExprWithTagsNode;


@NodeInfo(cost = NodeCost.NONE)
public final class SequenceNode extends ExprWithTagsNode {
  @Children private final ExpressionNode[] expressions;

  public SequenceNode(final ExpressionNode[] expressions, final SourceSection source) {
    super(source);
    assert source != null;
    this.expressions = expressions;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    executeAllButLast(frame);
    return expressions[expressions.length - 1].executeGeneric(frame);
  }

  @ExplodeLoop
  private void executeAllButLast(final VirtualFrame frame) {
    for (int i = 0; i < expressions.length - 1; i++) {
      expressions[i].executeGeneric(frame);
    }
  }

  @Override
  public boolean isResultUsed(final ExpressionNode child) {
    if (SOMNode.unwrapIfNecessary(expressions[expressions.length - 1]) == child) {
      Node parent = getParent();
      assert parent != null;
      if (parent instanceof ExpressionNode) {
        return ((ExpressionNode) parent).isResultUsed(this);
      }
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return "SeqNode[" + getSourceSection() + "]";
  }
}
