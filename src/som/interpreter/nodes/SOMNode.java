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
import java.util.Arrays;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.InlinerAdaptToEmbeddedOuterContext;
import som.interpreter.InlinerForLexicallyEmbeddedMethods;
import som.interpreter.SplitterForLexicallyEmbeddedCode;
import som.interpreter.Types;


@TypeSystemReference(Types.class)
public abstract class SOMNode extends Node {

  protected final SourceSection sourceSection;

  public SOMNode(final SourceSection sourceSection) {
    super();
    this.sourceSection = sourceSection;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
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

  private static Field[] getAllFields(final Class<? extends Object> clazz) {
    Field[] declaredFields = clazz.getDeclaredFields();
    if (clazz.getSuperclass() != null) {
      return concatArrays(getAllFields(clazz.getSuperclass()), declaredFields);
    }
    return declaredFields;
  }

  private static <T> T[] concatArrays(final T[] first, final T[] second) {
    T[] result = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(second, 0, result, first.length, second.length);
    return result;
  }

  private boolean assertNodeHasNoFrameSlots() {
    if (this.getClass().desiredAssertionStatus()) {
      for (Field f : getAllFields(getClass())) {
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

  public static Node unwrapIfNecessary(final Node node) {
    if (node instanceof WrapperNode) {
      return ((WrapperNode) node).getDelegateNode();
    } else {
      return node;
    }
  }

  public static Node getParentIgnoringWrapper(final Node node) {
    assert !(node instanceof WrapperNode) : "A correct usage will not see nodes that are wrappers. This is to detect bugs";

    Node parent = node.getParent();
    if (parent instanceof WrapperNode) {
      return parent.getParent();
    } else {
      return node;
    }
  }
}
