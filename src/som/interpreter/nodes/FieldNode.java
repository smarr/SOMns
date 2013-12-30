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

import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SObject;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;


@NodeChild(value = "self", type = SelfReadNode.class)
public abstract class FieldNode extends ExpressionNode {

  protected final int fieldIndex;

  public FieldNode(final int fieldIndex) {
    this.fieldIndex = fieldIndex;
  }

  public abstract static class FieldReadNode extends FieldNode {
    public FieldReadNode(final int fieldIndex)     { super(fieldIndex); }
    public FieldReadNode(final FieldReadNode node) { super(node.fieldIndex); }

    @Specialization
    public SAbstractObject read(final SObject self) {
      return self.getField(fieldIndex);
    }
  }

  @NodeChildren({
    @NodeChild(value = "self",  type = SelfReadNode.class),
    @NodeChild(value = "value", type = ExpressionNode.class)})
  public abstract static class FieldWriteNode extends FieldNode {
    private final Universe universe;

    public FieldWriteNode(final int fieldIndex, final Universe universe) {
      super(fieldIndex);
      this.universe = universe;
    }

    public FieldWriteNode(final FieldWriteNode node) {
      this(node.fieldIndex, node.universe);
    }

    @Specialization(order = 1)
    public SAbstractObject doSAbstractObject(final SObject self, final SAbstractObject value) {
      self.setField(fieldIndex, value);
      return value;
    }

    @Specialization(order = 20)
    public int doInteger(final SObject self, final int value) {
      self.setField(fieldIndex, universe.newInteger(value));
      return value;
    }

    @Specialization(order = 30)
    public BigInteger doBigInteger(final SObject self, final BigInteger value) {
      self.setField(fieldIndex, universe.newBigInteger(value));
      return value;
    }

    @Specialization(order = 40)
    public double doDouble(final SObject self, final double value) {
      self.setField(fieldIndex, universe.newDouble(value));
      return value;
    }

    @Specialization(order = 50)
    public String doString(final SObject self, final String value) {
      self.setField(fieldIndex, universe.newString(value));
      return value;
    }
  }
}
