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

import som.interpreter.nodes.messages.KeywordMonomorphicNode;
import som.vm.Universe;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import som.vmobjects.SMethod;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@NodeChildren({
  @NodeChild(value = "receiver",  type = ExpressionNode.class),
  @NodeChild(value = "arguments", type = ArgumentEvaluationNode.class)
})
public abstract class KeywordMessageNode extends AbstractMessageNode {

  public KeywordMessageNode(final SSymbol selector, final Universe universe) {
    super(selector, universe);
  }

  public KeywordMessageNode(final KeywordMessageNode node) {
    this(node.selector, node.universe);
  }

  public abstract ExpressionNode getReceiver();
  public abstract ArgumentEvaluationNode getArguments();
  public abstract Object executeEvaluated(final VirtualFrame frame, final Object receiver, Object arguments);

  // TODO: want to use @Generic here!
  @Specialization
  public Object doGeneric(final VirtualFrame frame, final Object rcvr,
      final Object arguments) {
    CompilerDirectives.transferToInterpreter();

    SAbstractObject receiver = (SAbstractObject) rcvr;

    SClass rcvrClass = classOfReceiver(receiver, getReceiver());
    SMethod invokable = rcvrClass.lookupInvokable(selector);

    if (invokable != null) {
      KeywordMonomorphicNode node = NodeFactory.createKeywordMonomorphicNode(selector, universe, rcvrClass, invokable, getReceiver(), getArguments());
      return replace(node, "Be optimisitic and do a monomorphic lookup cache, or a primitive inline.").
          executeEvaluated(frame, receiver, arguments);
    } else {
      SAbstractObject[] args = (SAbstractObject[]) arguments;
      return doFullSend(frame, receiver, args, rcvrClass);
    }
  }

  @Override
  public ExpressionNode cloneForInlining() {
    // TODO: test whether this is problematic
    return (ExpressionNode) this.copy();
    //return NodeFactory.createKeywordMessageNode(selector, universe, getReceiver(), getArguments());
  }
}
