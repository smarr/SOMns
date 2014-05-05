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
import som.interpreter.objectstorage.FieldNode.AbstractReadFieldNode;
import som.interpreter.objectstorage.FieldNode.AbstractWriteFieldNode;
import som.interpreter.objectstorage.FieldNode.UninitializedReadFieldNode;
import som.interpreter.objectstorage.FieldNode.UninitializedWriteFieldNode;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class FieldNode extends ExpressionNode {

  @Child protected ExpressionNode self;

  protected FieldNode(final ExpressionNode self) {
    this.self = self;
  }

  public final boolean accessesLocalSelf() {
    if (self instanceof UninitializedVariableReadNode) {
      UninitializedVariableReadNode selfRead =
          (UninitializedVariableReadNode) self;
      return selfRead.accessesSelf() && !selfRead.accessesOuterContext();
    }
    return false;
  }

  public static final class FieldReadNode extends FieldNode
      implements PreevaluatedExpression {
    @Child private AbstractReadFieldNode read;

    public FieldReadNode(final ExpressionNode self, final int fieldIndex) {
      super(self);
      read = new UninitializedReadFieldNode(fieldIndex);
    }

    public FieldReadNode(final FieldReadNode node) {
      this(node.self, node.read.getFieldIndex());
    }

    public Object executeEvaluated(final SObject obj) {
       return read.read(obj);
    }

    @Override
    public Object executePreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return executeEvaluated((SObject) arguments[0]);
    }

    @Override
    public long executeLong(final VirtualFrame frame) throws UnexpectedResultException {
      SObject obj = self.executeSObject(frame);
      return read.readLong(obj);
    }

    @Override
    public double executeDouble(final VirtualFrame frame) throws UnexpectedResultException {
      SObject obj = self.executeSObject(frame);
      return read.readDouble(obj);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      SObject obj;
      try {
        obj = self.executeSObject(frame);
      } catch (UnexpectedResultException e) {
        throw new RuntimeException("This should never happen by construction");
      }
      return executeEvaluated(obj);
    }

    @Override
    public void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  public static final class FieldWriteNode extends FieldNode
      implements PreevaluatedExpression {
    @Child private ExpressionNode value;
    @Child private AbstractWriteFieldNode write;

    public FieldWriteNode(final ExpressionNode self, final ExpressionNode value,
        final int fieldIndex) {
      super(self);
      this.value = value;
      write = new UninitializedWriteFieldNode(fieldIndex);
    }

    public FieldWriteNode(final FieldWriteNode node) {
      this(node.self, node.value, node.write.getFieldIndex());
    }

    public Object executeEvaluated(final VirtualFrame frame, final SObject self, final Object value) {
      return write.write(self, value);
    }

    @Override
    public Object executePreEvaluated(final VirtualFrame frame,
        final Object[] arguments) {
      return executeEvaluated(frame, (SObject) arguments[0], arguments[1]);
    }

    @Override
    public long executeLong(final VirtualFrame frame)
        throws UnexpectedResultException {
      SObject obj = executeSelf(frame);
      long val = value.executeLong(frame);
      return write.write(obj, val);
    }

    @Override
    public double executeDouble(final VirtualFrame frame)
        throws UnexpectedResultException {
      SObject obj = executeSelf(frame);
      double val  = value.executeDouble(frame);
      return write.write(obj, val);
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      SObject obj = executeSelf(frame);
      Object  val = value.executeGeneric(frame);
      return executeEvaluated(frame, obj, val);
    }

    private SObject executeSelf(final VirtualFrame frame) {
      SObject obj;
      try {
        obj = self.executeSObject(frame);
      } catch (UnexpectedResultException e) {
        throw new RuntimeException("This should never happen by construction");
      }
      return obj;
    }

    @Override
    public void executeVoid(final VirtualFrame frame) {
      executeGeneric(frame);
    }
  }
}
