/**
 * Copyright (c) 2013 Stefan Marr,   stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
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

package som.vmobjects;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.AccessModifier;
import som.compiler.MixinDefinition;
import som.interpreter.Invokable;
import som.interpreter.Method;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.CachedDispatchNode;
import som.interpreter.nodes.dispatch.DispatchGuard;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.interpreter.nodes.dispatch.LexicallyBoundDispatchNode;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import tools.snapshot.nodes.BlockSerializationNode.FrameSerializationNode;
import tools.snapshot.nodes.BlockSerializationNodeFactory.FrameSerializationNodeFactory;


public class SInvokable extends SAbstractObject implements Dispatchable {

  private final AccessModifier accessModifier;
  private final Invokable      invokable;
  private final RootCallTarget callTarget;
  private final SSymbol        signature;
  private final SInvokable[]   embeddedBlocks;

  @CompilationFinal private FrameSerializationNode frameSerializer;
  @CompilationFinal private MixinDefinition        holder;
  @CompilationFinal private RootCallTarget         atomicCallTarget;

  public SInvokable(final SSymbol signature,
      final AccessModifier accessModifier,
      final Invokable invokable, final SInvokable[] embeddedBlocks) {
    this.signature = signature;
    this.accessModifier = accessModifier;

    this.invokable = invokable;
    this.callTarget = invokable.createCallTarget();
    this.embeddedBlocks = embeddedBlocks;
  }

  public static class SInitializer extends SInvokable {

    public SInitializer(final SSymbol signature,
        final AccessModifier accessModifier,
        final Invokable invokable, final SInvokable[] embeddedBlocks) {
      super(signature, accessModifier, invokable, embeddedBlocks);
    }

    @Override
    public boolean isInitializer() {
      return true;
    }
  }

  public final SInvokable[] getEmbeddedBlocks() {
    return embeddedBlocks;
  }

  @Override
  public final SClass getSOMClass() {
    assert Classes.methodClass != null;
    return Classes.methodClass;
  }

  @Override
  public final boolean isValue() {
    return true;
  }

  @Override
  public boolean isInitializer() {
    return false;
  }

  public final RootCallTarget getCallTarget() {
    return callTarget;
  }

  @TruffleBoundary
  public final RootCallTarget getAtomicCallTarget() {
    if (atomicCallTarget == null) {
      synchronized (this) {
        if (atomicCallTarget == null) {
          Invokable atomicIvk = invokable.createAtomic();
          atomicCallTarget = atomicIvk.createCallTarget();
        }
      }
    }
    return atomicCallTarget;
  }

  public final Invokable getInvokable() {
    return invokable;
  }

  public final SSymbol getSignature() {
    return signature;
  }

  public final MixinDefinition getHolder() {
    assert holder != null;
    return holder;
  }

  /**
   * This method is meant for the language server,
   * it doesn't check whether the holder is set.
   */
  public final MixinDefinition getHolderUnsafe() {
    return holder;
  }

  public final void setHolder(final MixinDefinition value) {
    assert value != null;
    transferToInterpreterAndInvalidate("SMethod.setHolder");
    holder = value;
  }

  public final int getNumberOfArguments() {
    return getSignature().getNumberOfSignatureArguments();
  }

  public final Object invoke(final Object[] arguments) {
    return callTarget.call(arguments);
  }

  @Override
  public final Object invoke(final IndirectCallNode node, final Object[] arguments) {
    return node.call(callTarget, arguments);
  }

  @Override
  public final String toString() {
    if (holder == null) {
      return "Method(nil>>" + getSignature().toString() + ")";
    }

    return "Method(" + getHolder().getName().getString() + ">>" + getSignature().toString()
        + ")";
  }

  @Override
  public final AccessModifier getAccessModifier() {
    return accessModifier;
  }

  public final SourceSection getSourceSection() {
    return invokable.getSourceSection();
  }

  public FrameSerializationNode getFrameSerializer() {
    if (frameSerializer == null) {
      CompilerDirectives.transferToInterpreter();
      FrameDescriptor fd = ((Method) invokable).getLexicalScope().getOuterMethod()
                                               .getMethod().getFrameDescriptor();
      frameSerializer = FrameSerializationNodeFactory.create(fd);
    }
    return frameSerializer;
  }

  @Override
  public final AbstractDispatchNode getDispatchNode(final Object rcvr,
      final Object firstArg, final AbstractDispatchNode next, final boolean forAtomic) {
    assert next != null : "Pass the old node, just need the source section";

    CallTarget ct = forAtomic ? getAtomicCallTarget() : callTarget;

    // In case it's a private method, it is directly linked and doesn't need guards
    if (accessModifier == AccessModifier.PRIVATE) {
      return new LexicallyBoundDispatchNode(next.getSourceSection(), ct);
    }

    DispatchGuard guard = DispatchGuard.create(rcvr);
    return new CachedDispatchNode(ct, guard, next);
  }

  @Override
  public final String typeForErrors() {
    return "method";
  }

  @TruffleBoundary
  private URI getURI(final SourceSection source) {
    return source.getSource().getURI();
  }

  public SSymbol getIdentifier() {
    if (holder != null) {
      return Symbols.symbolFor(
          holder.getIdentifier().getString() + "." + this.signature.getString());
    } else if (invokable.getSourceSection() != null) {
      // TODO find a better solution than charIndex
      Path absolute = Paths.get(getURI(invokable.getSourceSection()));
      Path relative =
          Paths.get(VmSettings.BASE_DIRECTORY).toAbsolutePath().relativize(absolute);
      return Symbols.symbolFor(relative.toString() + ":"
          + invokable.getSourceSection().getCharIndex() + ":" + this.signature.getString());
    } else {
      return this.signature;
    }
  }
}
