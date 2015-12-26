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

import som.interpreter.FrameOnStackMarker;
import som.interpreter.InlinerAdaptToEmbeddedOuterContext;
import som.interpreter.InlinerForLexicallyEmbeddedMethods;
import som.interpreter.ReturnException;
import som.interpreter.SArguments;
import som.interpreter.SplitterForLexicallyEmbeddedCode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;


public final class ReturnNonLocalNode extends ContextualNode {

  @Child private ExpressionNode expression;
  private final BranchProfile blockEscaped;
  private final FrameSlot frameOnStackMarker;

  public ReturnNonLocalNode(final ExpressionNode expression,
      final FrameSlot frameOnStackMarker,
      final int outerSelfContextLevel,
      final SourceSection source) {
    super(outerSelfContextLevel, source);
    assert outerSelfContextLevel > 0;
    this.expression = expression;
    this.blockEscaped = BranchProfile.create();
    this.frameOnStackMarker = frameOnStackMarker;
  }

  public ReturnNonLocalNode(final ReturnNonLocalNode node,
      final FrameSlot inlinedFrameOnStack) {
    this(node.expression, inlinedFrameOnStack,
        node.contextLevel, node.getSourceSection());
  }

  private FrameOnStackMarker getMarkerFromContext(final MaterializedFrame ctx) {
    return (FrameOnStackMarker) FrameUtil.getObjectSafe(ctx, frameOnStackMarker);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    Object result = expression.executeGeneric(frame);

    MaterializedFrame ctx = determineContext(frame);
    FrameOnStackMarker marker = getMarkerFromContext(ctx);

    if (marker.isOnStack()) {
      throw new ReturnException(result, marker);
    } else {
      blockEscaped.enter();
      SBlock block = (SBlock) SArguments.rcvr(frame);
      Object self = SArguments.rcvr(ctx);
      return SAbstractObject.sendEscapedBlock(self, block);
    }
  }

  @Override
  public void replaceWithIndependentCopyForInlining(final SplitterForLexicallyEmbeddedCode inliner) {
    FrameSlot inlinedFrameOnStack  = inliner.getFrameSlot(this, frameOnStackMarker.getIdentifier());
    assert inlinedFrameOnStack  != null;
    replace(new ReturnNonLocalNode(this, inlinedFrameOnStack));
  }

  @Override
  public void replaceWithLexicallyEmbeddedNode(
      final InlinerForLexicallyEmbeddedMethods inlinerForLexicallyEmbeddedMethods) {
    ExpressionNode inlined;
    if (contextLevel == 1) {
      inlined = new ReturnLocalNode(expression, frameOnStackMarker,
          getSourceSection());
    } else {
      inlined = new ReturnNonLocalNode(expression,
        frameOnStackMarker, contextLevel - 1, getSourceSection());
    }
    replace(inlined);
  }

  @Override
  public void replaceWithCopyAdaptedToEmbeddedOuterContext(
      final InlinerAdaptToEmbeddedOuterContext inliner) {
    // if the context level is 1, the variable is in the outer context,
    // which just got inlined, so, we need to adapt the slot id
    assert !inliner.appliesTo(contextLevel);
    // this case should not happen, because the frame slot is at the root level
    // so, anything that got embedded, has to be nested at least once

    if (inliner.needToAdjustLevel(contextLevel)) {
      ReturnNonLocalNode node = new ReturnNonLocalNode(
          expression, frameOnStackMarker, contextLevel - 1, getSourceSection());
      replace(node);
      return;
    }
  }

  /**
   * Normally, there are no local returns in SOM. However, after
   * inlining/embedding of blocks, we need this ReturnLocalNode to replace
   * previous non-local returns.
   */
  private static final class ReturnLocalNode extends ExpressionNode {
    @Child private ExpressionNode expression;
    private final FrameSlot frameOnStackMarker;

    private ReturnLocalNode(final ExpressionNode exp,
        final FrameSlot frameOnStackMarker, final SourceSection source) {
      super(source);
      this.expression = exp;
      this.frameOnStackMarker = frameOnStackMarker;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      Object result = expression.executeGeneric(frame);

      FrameOnStackMarker marker = (FrameOnStackMarker) FrameUtil.getObjectSafe(
          frame, frameOnStackMarker);

      // this ReturnLocalNode should only become part of an AST because of
      // inlining a literal block, and that block, should never be
      // captured as a value and passed around. Because, we should only ever
      // do the inlining for blocks where we know this doesn't happen.
      assert marker.isOnStack();
      throw new ReturnException(result, marker);

//      if (marker.isOnStack()) {
//      } else {
//        throw new RuntimeException("This should never happen");
//        blockEscaped.enter();
//        SBlock block = (SBlock) SArguments.rcvr(frame);
//        Object self = SArguments.rcvr(ctx);
//        return SAbstractObject.sendEscapedBlock(self, block);
//      }
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final SplitterForLexicallyEmbeddedCode inliner) {
      FrameSlot inlinedFrameOnStack  = inliner.getLocalFrameSlot(frameOnStackMarker.getIdentifier());
      assert inlinedFrameOnStack  != null;
      replace(new ReturnLocalNode(expression, inlinedFrameOnStack,
          getSourceSection()));
    }
  }

  public static final class CatchNonLocalReturnNode extends ExpressionNode {
    @Child protected ExpressionNode methodBody;
    private final BranchProfile nonLocalReturnHandler;
    private final BranchProfile doCatch;
    private final BranchProfile doPropagate;
    private final FrameSlot frameOnStackMarker;

    public CatchNonLocalReturnNode(final ExpressionNode methodBody,
        final FrameSlot frameOnStackMarker) {
      super(null);
      this.methodBody = methodBody;
      this.nonLocalReturnHandler = BranchProfile.create();
      this.frameOnStackMarker    = frameOnStackMarker;

      this.doCatch = BranchProfile.create();
      this.doPropagate = BranchProfile.create();
    }

    @Override
    public ExpressionNode getFirstMethodBodyNode() {
      return methodBody;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      FrameOnStackMarker marker = new FrameOnStackMarker();
      frameOnStackMarker.setKind(FrameSlotKind.Object);
      frame.setObject(frameOnStackMarker, marker);

      try {
        return methodBody.executeGeneric(frame);
      } catch (ReturnException e) {
        nonLocalReturnHandler.enter();
        if (!e.reachedTarget(marker)) {
          doPropagate.enter();
          marker.frameNoLongerOnStack();
          throw e;
        } else {
          doCatch.enter();
          return e.result();
        }
      } finally {
        marker.frameNoLongerOnStack();
      }
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final SplitterForLexicallyEmbeddedCode inliner) {
      FrameSlot inlinedFrameOnStackMarker = inliner.getLocalFrameSlot(frameOnStackMarker.getIdentifier());
      assert inlinedFrameOnStackMarker != null;
      replace(new CatchNonLocalReturnNode(methodBody, inlinedFrameOnStackMarker));
    }
  }
}
