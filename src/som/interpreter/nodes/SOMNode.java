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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.InliningVisitor;
import som.interpreter.Types;
import tools.dym.Tags;


@TypeSystemReference(Types.class)
public abstract class SOMNode extends Node {

  @CompilationFinal protected SourceSection sourceSection;

  @SuppressWarnings("unchecked")
  public <T extends SOMNode> T initialize(final SourceSection sourceSection) {
    assert sourceSection != null;
    assert this.sourceSection == null : "sourceSection should only be set once";
    this.sourceSection = sourceSection;
    return (T) this;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  /**
   * This method is called by a visitor to adjust nodes that access lexical
   * elements such as locals or arguments. This is necessary after changes in
   * the scope tree. This can be caused by method splitting to obtain
   * independent copies, or inlining of blocks to adjust context levels.
   *
   * <p>
   * When such changes occurred, all blocks within that tree need to be adjusted.
   */
  public void replaceAfterScopeChange(final InliningVisitor inliner) {
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

  @SuppressWarnings("unchecked")
  public static <T extends Node> T unwrapIfNecessary(final T node) {
    if (node instanceof WrapperNode) {
      return (T) ((WrapperNode) node).getDelegateNode();
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
      return parent;
    }
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == Tags.AnyNode.class) {
      return true;
    } else {
      return false;
    }
  }
}
