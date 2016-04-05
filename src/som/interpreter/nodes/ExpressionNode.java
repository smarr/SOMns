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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.TypesGen;
import som.interpreter.actors.SFarReference;
import som.interpreter.actors.SPromise;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;
import tools.dym.Tags.ControlFlowCondition;
import tools.dym.Tags.LoopBody;
import tools.dym.Tags.PrimitiveArgument;
import tools.dym.Tags.VirtualInvokeReceiver;


@Instrumentable(factory = ExpressionNodeWrapper.class)
public abstract class ExpressionNode extends SOMNode {

  @CompilationFinal private byte tagMark;

  /**
   * Indicates that this is the subnode of a RootNode,
   * possibly only {@link som.interpreter.Method}.
   * TODO: figure out whether we leave out primitives.
   */
  private final static byte ROOT_EXPR = 1;

  /**
   * Indicates that this node is a root of a loop body.
   */
  private final static byte LOOP_BODY = 1 << 1;

  /**
   * Indicates that this node is a root for a control flow condition
   */
  private final static byte CONTROL_FLOW_CONDITION = 1 << 2;

  /**
   * Indicates that this node is an argument to a primitive.
   */
  private final static byte PRIMITIVE_ARGUMENT = 1 << 3;

  private final static byte VIRTUAL_INVOKE_RECEIVER = 1 << 4;

  public ExpressionNode(final SourceSection sourceSection) {
    super(sourceSection);
  }

  /**
   * Use for wrapping node only.
   */
  protected ExpressionNode(final ExpressionNode wrappedNode) {
    super(null);
  }

  private boolean isTagged(final byte mask) {
    return (tagMark & mask) != 0;
  }

  private void tagWith(final byte mask) {
    tagMark |= mask;
  }

  /**
   * Mark the node as being a root expression: {@link RootTag}.
   */
  public void markAsRootExpression() {
    assert !isTagged(ROOT_EXPR);
    assert !isTagged(LOOP_BODY);
    assert !isTagged(PRIMITIVE_ARGUMENT);
    assert !isTagged(CONTROL_FLOW_CONDITION);
    assert getSourceSection() != null;
    tagWith(ROOT_EXPR);
  }

  public void markAsLoopBody() {
    assert !isTagged(LOOP_BODY);
    assert !isTagged(ROOT_EXPR);
    assert !isTagged(CONTROL_FLOW_CONDITION);
    assert getSourceSection() != null;
    tagWith(LOOP_BODY);
  }

  public void markAsControlFlowCondition() {
    assert !isTagged(LOOP_BODY);
    assert !isTagged(ROOT_EXPR);
    assert !isTagged(CONTROL_FLOW_CONDITION);
    assert getSourceSection() != null;
    tagWith(CONTROL_FLOW_CONDITION);
  }

  public void markAsPrimitiveArgument() {
    assert getSourceSection() != null;
    tagWith(PRIMITIVE_ARGUMENT);
  }

  public void markAsVirtualInvokeReceiver() {
    assert getSourceSection() != null;
    tagWith(VIRTUAL_INVOKE_RECEIVER);
  }

  @Override
  protected boolean isTaggedWith(final Class<?> tag) {
    if (tag == StatementTag.class) {
      return true;
    } else if (tag == RootTag.class) {
      return isTagged(ROOT_EXPR);
    } else if (tag == LoopBody.class) {
      return isTagged(LOOP_BODY);
    } else if (tag == ControlFlowCondition.class) {
      return isTagged(CONTROL_FLOW_CONDITION);
    } else if (tag == PrimitiveArgument.class) {
      return isTagged(PRIMITIVE_ARGUMENT);
    } else if (tag == VirtualInvokeReceiver.class) {
      return isTagged(VIRTUAL_INVOKE_RECEIVER);
    } else {
      return super.isTaggedWith(tag);
    }
  }

  public abstract Object executeGeneric(final VirtualFrame frame);

  @Override
  public ExpressionNode getFirstMethodBodyNode() { return this; }

  public boolean executeBoolean(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectBoolean(executeGeneric(frame));
  }

  public long executeLong(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectLong(executeGeneric(frame));
  }

  public BigInteger executeBigInteger(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectBigInteger(executeGeneric(frame));
  }

  public String executeString(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectString(executeGeneric(frame));
  }

  public double executeDouble(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectDouble(executeGeneric(frame));
  }

  public SSymbol executeSSymbol(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectSSymbol(executeGeneric(frame));
  }

  public SBlock executeSBlock(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectSBlock(executeGeneric(frame));
  }

  public SClass executeSClass(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectSClass(executeGeneric(frame));
  }

  public SInvokable executeSInvokable(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectSInvokable(executeGeneric(frame));
  }

  public SObject executeSObject(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectSObject(executeGeneric(frame));
  }

  public SArray executeSArray(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectSArray(executeGeneric(frame));
  }

  public SAbstractObject executeSAbstractObject(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectSAbstractObject(executeGeneric(frame));
  }

  public Object[] executeObjectArray(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectObjectArray(executeGeneric(frame));
  }

  public SFarReference executeSFarReference(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectSFarReference(executeGeneric(frame));
  }

  public SPromise executeSPromise(final VirtualFrame frame) throws UnexpectedResultException {
    return TypesGen.expectSPromise(executeGeneric(frame));
  }

  public boolean isResultUsed(final ExpressionNode child) {
    return true;
  }
}
