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

import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.UninitializedReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.UninitializedWriteFieldNode;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;

public abstract class FieldNode extends ExpressionNode {

  protected FieldNode(final SourceSection source, final boolean executesEnforced) {
    super(source, executesEnforced);
  }

  protected abstract ExpressionNode getSelf();

  public final boolean accessesLocalSelf() {
    if (getSelf() instanceof UninitializedVariableReadNode) {
      UninitializedVariableReadNode selfRead =
          (UninitializedVariableReadNode) getSelf();
      return selfRead.accessesSelf() && !selfRead.accessesOuterContext();
    }
    return false;
  }

  public abstract static class AbstractFieldReadNode extends FieldNode implements PreevaluatedExpression {
    @Child protected ExpressionNode self;

    public AbstractFieldReadNode(final ExpressionNode self,
        final SourceSection source, final boolean executesEnforced) {
      super(source, executesEnforced);
      this.self = self;
    }

    @Override
    protected final ExpressionNode getSelf() {
      return self;
    }

    public abstract Object executeEvaluated(final VirtualFrame frame, final SObject obj);

    @Override
    public final Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return executeEvaluated(frame, (SObject) arguments[0]);
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      try {
        SObject obj = self.executeSObject(frame);
        return executeEvaluated(frame, obj);
      } catch (UnexpectedResultException e) {
        CompilerDirectives.transferToInterpreter();
        throw new RuntimeException("This should never happen by construction");
      }
    }
  }

  public static final class UnenforcedFieldReadNode extends AbstractFieldReadNode {
    @Child private AbstractReadFieldNode read;

    public UnenforcedFieldReadNode(final ExpressionNode self, final int fieldIndex,
        final SourceSection source) {
      super(self, source, false);
      read = new UninitializedReadFieldNode(fieldIndex); // TODO: there needs to be a variant for enforced!
    }

    public UnenforcedFieldReadNode(final UnenforcedFieldReadNode node) {
      this(node.self, node.read.getFieldIndex(), node.getSourceSection());
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final SObject obj) {
      assert !executesEnforced;
      return read.read(obj);
    }

    @Override
    public long executeLong(final VirtualFrame frame) throws UnexpectedResultException {
      assert !executesEnforced;
      SObject obj = self.executeSObject(frame);
      return read.readLong(obj);
    }

    @Override
    public double executeDouble(final VirtualFrame frame) throws UnexpectedResultException {
      assert !executesEnforced;
      SObject obj = self.executeSObject(frame);
      return read.readDouble(obj);
    }

    @Override
    public void executeVoid(final VirtualFrame frame) {
      assert !executesEnforced;
      /* NOOP, side effect free */ }
  }

  @NodeChildren({
    @NodeChild(value = "self", type = ExpressionNode.class),
    @NodeChild(value = "value", type = ExpressionNode.class)})
  public abstract static class AbstractFieldWriteNode extends FieldNode
      implements PreevaluatedExpression {
    public AbstractFieldWriteNode(final SourceSection source, final boolean executesEnforced) {
      super(source, executesEnforced);
    }

    public abstract Object executeEvaluated(final VirtualFrame frame,
        final SObject self, final Object value);

    @Override
    public final Object doPreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return executeEvaluated(frame, (SObject) arguments[0], arguments[1]);
    }

    @Override
    public void executeVoid(final VirtualFrame frame) {
      executeGeneric(frame);
    }
  }

  public abstract static class UnenforcedFieldWriteNode extends AbstractFieldWriteNode {
    @Child private AbstractWriteFieldNode write;

    public UnenforcedFieldWriteNode(final int fieldIndex, final SourceSection source) {
      super(source, false);
      write = new UninitializedWriteFieldNode(fieldIndex);
    }

    public UnenforcedFieldWriteNode(final UnenforcedFieldWriteNode node) {
      this(node.write.getFieldIndex(), node.getSourceSection());
    }

    @Override
    public final Object executeEvaluated(final VirtualFrame frame,
        final SObject self, final Object value) {
      assert !executesEnforced;
      return write.write(self, value);
    }

    @Specialization
    public long doLong(final VirtualFrame frame, final SObject self,
        final long value) {
      assert !executesEnforced;
      return write.write(self, value);
    }

    @Specialization
    public double doDouble(final VirtualFrame frame, final SObject self,
        final double value) {
      assert !executesEnforced;
      return write.write(self, value);
    }

    @Specialization
    public Object doObject(final VirtualFrame frame, final SObject self,
        final Object value) {
      return executeEvaluated(frame, self, value);
    }
  }
}
