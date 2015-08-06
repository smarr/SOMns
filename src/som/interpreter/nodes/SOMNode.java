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

import java.lang.reflect.Field;

import som.interpreter.InlinerAdaptToEmbeddedOuterContext;
import som.interpreter.InlinerForLexicallyEmbeddedMethods;
import som.interpreter.SplitterForLexicallyEmbeddedCode;
import som.interpreter.Types;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

@TypeSystemReference(Types.class)
public abstract class SOMNode extends Node {

  public SOMNode(final SourceSection sourceSection) {
    super(sourceSection);
  }

  /**
   * This method is called by a visitor that adjusts a newly split copy of a
   * block method to refer to the correct out lexical context, and for instance,
   * to replace FrameSlot references by the correct and independent new outer
   * lexical scope.
   * @param inliner
   */
  public void replaceWithIndependentCopyForInlining(
      final SplitterForLexicallyEmbeddedCode inliner) {
    // do nothing!
    // only a small subset of nodes needs to implement this method.
    // Most notably, nodes using FrameSlots, and block nodes with method
    // nodes.
    assert assertNodeHasNoFrameSlots();
  }

  private boolean assertNodeHasNoFrameSlots() {
    if (this.getClass().desiredAssertionStatus()) {
      for (Field f : NodeUtil.getAllFields(getClass())) {
        assert f.getType() != FrameSlot.class;
        if (f.getType() == FrameSlot.class) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * This method is called by a visitor that adjusts a copy of a block method
   * to be embedded into its outer method/block. Thus, it needs to adjust
   * frame slots, which are now moved up to the outer method, and also
   * trigger adaptation of methods lexically embedded/included in this copy.
   * The actual adaptation of those methods is done by
   * replaceWithCopyAdaptedToEmbeddedOuterContext();
   * @param inlinerForLexicallyEmbeddedMethods
   */
  public void replaceWithLexicallyEmbeddedNode(
      final InlinerForLexicallyEmbeddedMethods inliner) {
    // do nothing!
    // only a small subset of nodes needs to implement this method.
    // Most notably, nodes using FrameSlots, and block nodes with method
    // nodes.
    assert assertNodeHasNoFrameSlots();
  }

  /**
   * Adapt a copy of a method that is lexically enclosed in a block that
   * just got embedded into its outer context.
   * Thus, all frame slots need to be fixed up, as well as all embedded
   * blocks.
   * @param inlinerAdaptToEmbeddedOuterContext
   */
  public void replaceWithCopyAdaptedToEmbeddedOuterContext(
      final InlinerAdaptToEmbeddedOuterContext inliner) {
    // do nothing!
    // only a small subset of nodes needs to implement this method.
    // Most notably, nodes using FrameSlots, and block nodes with method
    // nodes.
    assert assertNodeHasNoFrameSlots();
  }

  /**
   * @return body of a node that just wraps the actual method body.
   */
  public abstract ExpressionNode getFirstMethodBodyNode();

  public static final RootNode getRootNodeAndTryReallyHard(final Node node) {
    int numAttempts = 0;

    RootNode root = null;
    while (root == null) {
      root = node.getRootNode();
      if (root == null && numAttempts > 3) {
        try { Thread.sleep(10 * numAttempts * numAttempts); } catch (InterruptedException e) { }
      }
      numAttempts++;

      assert numAttempts < 10 : "this should be plenty, now we give up";
    }
    return root;
  }
}
