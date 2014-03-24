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

import java.math.BigInteger;

import som.interpreter.nodes.UninitializedVariableNode.UninitializedVariableReadNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChild(value = "self", type = ExpressionNode.class)
public abstract class FieldNode extends ExpressionNode {

  @Child private FieldAccessor fieldAccessor;

  protected final int fieldIndex;

  public FieldNode(final int fieldIndex) {
    this.fieldIndex = fieldIndex;
    fieldAccessor = FieldAccessor.create(fieldIndex);
  }

  protected final Object readFromObject(final SObject obj) {
    return fieldAccessor.read(obj);
  }

  protected final void writeToObject(final SObject obj, final Object value) {
    fieldAccessor.write(obj, value);
  }

  public abstract ExpressionNode getSelf();

  public final boolean accessesLocalSelf() {
    ExpressionNode self = getSelf();

    if (self instanceof UninitializedVariableReadNode) {
      UninitializedVariableReadNode selfRead =
          (UninitializedVariableReadNode) self;
      return selfRead.accessesSelf() && !selfRead.accessesOuterContext();
    }
    return false;
  }

  public abstract static class FieldReadNode extends FieldNode
      implements PreevaluatedExpression {
    public FieldReadNode(final int fieldIndex)     { super(fieldIndex);     }
    public FieldReadNode(final FieldReadNode node) { this(node.fieldIndex); }

    public final int getFieldIndex() {
      return fieldIndex;
    }

    public abstract Object executeEvaluated(SObject self);

    @Override
    public final Object executePreEvaluated(final VirtualFrame frame,
        final Object receiver, final Object[] arguments) {
      return executeEvaluated((SObject) receiver);
    }

    protected final boolean isSAbstractObject(final SObject self) {
      return readFromObject(self) instanceof SAbstractObject;
    }

    protected final boolean isInteger(final SObject self) {
      return readFromObject(self) instanceof Integer;
    }

    protected final boolean isBigInteger(final SObject self) {
      return readFromObject(self) instanceof BigInteger;
    }

    protected final boolean isDouble(final SObject self) {
      return readFromObject(self) instanceof Double;
    }

    protected final boolean isString(final SObject self) {
      return readFromObject(self) instanceof String;
    }

    @Specialization(guards = "isString")
    public final String readString(final SObject self) {
      return (String) readFromObject(self);
    }

    @Specialization(guards = "isDouble")
    public final double readDouble(final SObject self) {
      return (double) readFromObject(self);
    }

    @Specialization(guards = "isSAbstractObject")
    public final SAbstractObject readSAbstractObject(final SObject self) {
      return (SAbstractObject) readFromObject(self);
    }

    @Specialization(guards = "isInteger")
    public final int readInteger(final SObject self) {
      return (int) readFromObject(self);
    }

    @Specialization(guards = "isBigInteger")
    public final BigInteger readBigInteger(final SObject self) {
      return (BigInteger) readFromObject(self);
    }

    @Override
    public final void executeVoid(final VirtualFrame frame) { /* NOOP, side effect free */ }
  }

  @NodeChildren({
    @NodeChild(value = "self",  type = ExpressionNode.class),
    @NodeChild(value = "value", type = ExpressionNode.class)})
  public abstract static class FieldWriteNode extends FieldNode
      implements PreevaluatedExpression {

    public FieldWriteNode(final int fieldIndex) {
      super(fieldIndex);
    }

    public FieldWriteNode(final FieldWriteNode node) {
      this(node.fieldIndex);
    }

    public int getFieldIndex() {
      return fieldIndex;
    }

    public abstract ExpressionNode getValue();

    public abstract Object executeEvaluated(VirtualFrame frame, SObject self, Object value);

    @Override
    public final Object executePreEvaluated(final VirtualFrame frame,
        final Object receiver, final Object[] arguments) {
      return executeEvaluated(frame, (SObject) receiver, arguments[0]);
    }

    @Specialization(order = 1)
    public final SAbstractObject doSAbstractObject(final SObject self, final SAbstractObject value) {
      writeToObject(self, value);
      return value;
    }

    @Specialization(order = 20)
    public final int doInteger(final SObject self, final int value) {
      writeToObject(self, value);
      return value;
    }

    @Specialization(order = 30)
    public final BigInteger doBigInteger(final SObject self, final BigInteger value) {
      writeToObject(self, value);
      return value;
    }

    @Specialization(order = 40)
    public final double doDouble(final SObject self, final double value) {
      writeToObject(self, value);
      return value;
    }

    @Specialization(order = 50)
    public final String doString(final SObject self, final String value) {
      writeToObject(self, value);
      return value;
    }
  }
}
