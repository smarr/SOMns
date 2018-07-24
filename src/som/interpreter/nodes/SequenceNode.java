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

import som.interpreter.nodes.nary.ExprWithTagsNode;


@NodeInfo(cost = NodeCost.NONE)
public abstract class SequenceNode extends ExprWithTagsNode {

  protected boolean isResultUsedByParent() {
    Node parent = getParent();
    assert parent != null;
    if (parent instanceof ExpressionNode) {
      return ((ExpressionNode) parent).isResultUsed(this);
    }
    return true;
  }

  @Override
  public abstract boolean isResultUsed(ExpressionNode child);

  public static final class SeqNNode extends SequenceNode {
    @Children private final ExpressionNode[] expressions;

    public SeqNNode(final ExpressionNode[] expressions) {
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
        return isResultUsedByParent();
      }
      return false;
    }
  }

  public static final class Seq2Node extends SequenceNode {
    @Child private ExpressionNode expr1;
    @Child private ExpressionNode expr2;

    public Seq2Node(final ExpressionNode expr1, final ExpressionNode expr2) {
      this.expr1 = expr1;
      this.expr2 = expr2;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      expr1.executeGeneric(frame);
      return expr2.executeGeneric(frame);
    }

    @Override
    public boolean isResultUsed(final ExpressionNode child) {
      if (SOMNode.unwrapIfNecessary(expr2) == child) {
        return isResultUsedByParent();
      }
      return false;
    }
  }

  public static final class Seq3Node extends SequenceNode {
    @Child private ExpressionNode expr1;
    @Child private ExpressionNode expr2;
    @Child private ExpressionNode expr3;

    public Seq3Node(final ExpressionNode expr1, final ExpressionNode expr2,
        final ExpressionNode expr3) {
      this.expr1 = expr1;
      this.expr2 = expr2;
      this.expr3 = expr3;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      expr1.executeGeneric(frame);
      expr2.executeGeneric(frame);
      return expr3.executeGeneric(frame);
    }

    @Override
    public boolean isResultUsed(final ExpressionNode child) {
      if (SOMNode.unwrapIfNecessary(expr3) == child) {
        return isResultUsedByParent();
      }
      return false;
    }
  }

  public static final class Seq4Node extends SequenceNode {
    @Child private ExpressionNode expr1;
    @Child private ExpressionNode expr2;
    @Child private ExpressionNode expr3;
    @Child private ExpressionNode expr4;

    public Seq4Node(final ExpressionNode expr1, final ExpressionNode expr2,
        final ExpressionNode expr3, final ExpressionNode expr4) {
      this.expr1 = expr1;
      this.expr2 = expr2;
      this.expr3 = expr3;
      this.expr4 = expr4;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      expr1.executeGeneric(frame);
      expr2.executeGeneric(frame);
      expr3.executeGeneric(frame);
      return expr4.executeGeneric(frame);
    }

    @Override
    public boolean isResultUsed(final ExpressionNode child) {
      if (SOMNode.unwrapIfNecessary(expr4) == child) {
        return isResultUsedByParent();
      }
      return false;
    }
  }

  @Override
  public String toString() {
    return "SeqNode[" + getSourceSection() + "]";
  }
}
