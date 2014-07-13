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
import som.interpreter.Inliner;
import som.interpreter.ReturnException;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SBlock;

import com.oracle.truffle.api.SourceSection;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;

public final class ReturnNonLocalNode extends ContextualNode {

  @Child private ExpressionNode expression;
  private final BranchProfile blockEscaped;
  private final FrameSlot frameOnStackMarker;
  private final FrameSlot outerSelfSlot;

  public ReturnNonLocalNode(final ExpressionNode expression,
      final FrameSlot frameOnStackMarker,
      final FrameSlot outerSelfSlot,
      final int outerSelfContextLevel,
      final FrameSlot localSelf, final SourceSection source) {
    super(outerSelfContextLevel, localSelf, source);
    this.expression = expression;
    this.blockEscaped = new BranchProfile();
    this.frameOnStackMarker = frameOnStackMarker;
    this.outerSelfSlot      = outerSelfSlot;
  }

  public ReturnNonLocalNode(final ReturnNonLocalNode node, final FrameSlot inlinedFrameOnStack,
      final FrameSlot inlinedOuterSelfSlot, final FrameSlot inlinedLocalSelfSlot) {
    this(node.expression, inlinedFrameOnStack, inlinedOuterSelfSlot,
        node.contextLevel, inlinedLocalSelfSlot, node.getSourceSection());
  }

  private FrameOnStackMarker getMarkerFromContext(final MaterializedFrame ctx) {
    return (FrameOnStackMarker) FrameUtil.getObjectSafe(ctx, frameOnStackMarker);
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    MaterializedFrame ctx = determineContext(frame);
    FrameOnStackMarker marker = getMarkerFromContext(ctx);

    if (marker.isOnStack()) {
      Object result = expression.executeGeneric(frame);
      throw new ReturnException(result, marker);
    } else {
      blockEscaped.enter();
      SBlock block = (SBlock) FrameUtil.getObjectSafe(frame, localSelf);
      Object self = FrameUtil.getObjectSafe(ctx, outerSelfSlot);
      return SAbstractObject.sendEscapedBlock(self, block);
    }
  }

  @Override
  public void executeVoid(final VirtualFrame frame) {
    executeGeneric(frame);
  }

  @Override
  public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
    FrameSlot localSelfSlot        = inliner.getLocalFrameSlot(getLocalSelfSlotIdentifier());
    FrameSlot inlinedFrameOnStack  = inliner.getFrameSlot(this, frameOnStackMarker.getIdentifier());
    FrameSlot inlinedOuterSelfSlot = inliner.getFrameSlot(this, outerSelfSlot.getIdentifier());

    assert localSelfSlot        != null;
    assert inlinedFrameOnStack  != null;
    assert inlinedOuterSelfSlot != null;
    replace(new ReturnNonLocalNode(this, inlinedFrameOnStack, inlinedOuterSelfSlot, localSelfSlot));
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
      this.nonLocalReturnHandler = new BranchProfile();
      this.frameOnStackMarker    = frameOnStackMarker;

      this.doCatch = new BranchProfile();
      this.doPropagate = new BranchProfile();
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

      Object result;
      try {
        result = methodBody.executeGeneric(frame);
      } catch (ReturnException e) {
        nonLocalReturnHandler.enter();
        if (!e.reachedTarget(marker)) {
          doPropagate.enter();
          marker.frameNoLongerOnStack();
          throw e;
        } else {
          doCatch.enter();
          result = e.result();
        }
      }

      marker.frameNoLongerOnStack();
      return result;
    }

    @Override
    public void executeVoid(final VirtualFrame frame) {
      FrameOnStackMarker marker = new FrameOnStackMarker();
      frameOnStackMarker.setKind(FrameSlotKind.Object);
      frame.setObject(frameOnStackMarker, marker);

      try {
        methodBody.executeVoid(frame);
      } catch (ReturnException e) {
        nonLocalReturnHandler.enter();
        if (!e.reachedTarget(marker)) {
          doPropagate.enter();
          marker.frameNoLongerOnStack();
          throw e;
        }
      }

      marker.frameNoLongerOnStack();
    }

    @Override
    public void replaceWithIndependentCopyForInlining(final Inliner inliner) {
      FrameSlot inlinedFrameOnStackMarker = inliner.getLocalFrameSlot(frameOnStackMarker.getIdentifier());
      assert inlinedFrameOnStackMarker != null;
      replace(new CatchNonLocalReturnNode(methodBody, inlinedFrameOnStackMarker));
    }
  }
}
