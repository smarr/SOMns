package som.primitives;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySystemOperation;
import som.primitives.PathPrims.FileModule;
import som.primitives.actors.PromisePrims;
import som.vm.Symbols;
import som.vmobjects.SArray;
import som.vmobjects.SBlock;
import som.vmobjects.SClass;
import som.vmobjects.SFileDescriptor;
import som.vmobjects.SSymbol;


public final class FilePrims {

  @GenerateNodeFactory
  @ImportStatic(FilePrims.class)
  @Primitive(primitive = "fileCreateFileDescriptorFor:")
  public abstract static class CreateFileDescriptorPrim extends UnarySystemOperation {
    @Specialization
    public final SFileDescriptor createFileDescriptor(final String file) {
      return new SFileDescriptor(file);
    }
  }

  @GenerateNodeFactory
  @ImportStatic(FilePrims.class)
  @Primitive(primitive = "fileDescriptorClass:")
  public abstract static class SetFileDescriptorClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SClass setClass(final SClass value) {
      SFileDescriptor.setSOMClass(value);
      return value;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "fileClose:")
  public abstract static class CloseFilePrim extends UnaryExpressionNode {
    @Child ExceptionSignalingNode ioException;

    @Override
    public ExpressionNode initialize(final SourceSection sourceSection,
        final boolean eagerlyWrapped) {
      super.initialize(sourceSection, eagerlyWrapped);
      ioException = insert(ExceptionSignalingNode.createNode(new FileModule(),
          Symbols.IOException, Symbols.SIGNAL_WITH, sourceSection));
      return this;
    }

    @Specialization
    public final Object closeFile(final SFileDescriptor file) {
      file.closeFile(ioException);
      return file;
    }
  }

  @GenerateNodeFactory
  @ImportStatic(PromisePrims.class)
  @Primitive(primitive = "fileBuffer:")
  public abstract static class FileBufferPrim extends UnaryExpressionNode {
    @Specialization
    public final SArray getBuffer(final SFileDescriptor file) {
      return file.getBuffer();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "fileBufferSize:")
  public abstract static class FileBufferSizePrim extends UnaryExpressionNode {
    @Specialization
    public final long getBufferSize(final SFileDescriptor file) {
      return file.getBufferSize();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "file:setBufferSize:")
  public abstract static class FileSetBufferSizePrim extends BinaryExpressionNode {
    @Specialization
    public final boolean setBufferSize(final SFileDescriptor file, final int size) {
      if (!file.isClosed()) {
        return false;
      }

      file.setBufferSize(size);
      return true;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "file:setMode:")
  public abstract static class FileSetModePrim extends BinaryExpressionNode {

    @Child protected ExceptionSignalingNode argumentError;

    @Override
    public ExpressionNode initialize(final SourceSection sourceSection,
        final boolean eagerlyWrapped) {
      super.initialize(sourceSection, eagerlyWrapped);
      argumentError = insert(ExceptionSignalingNode.createArgumentErrorNode(sourceSection));
      return this;
    }

    @Specialization
    public final Object setModeSymbol(final SFileDescriptor file, final SSymbol mode) {
      try {
        file.setMode(mode);
      } catch (IllegalArgumentException e) {
        argumentError.signal(mode.getString());
      }
      return file;
    }

    @Fallback
    public final Object setWithUnsupportedValue(final Object file, final Object mode) {
      argumentError.signal(errorMsg(mode));
      return file;
    }

    @TruffleBoundary
    private String errorMsg(final Object mode) {
      return "File access mode invalid, was: " + Objects.toString(mode) + " "
          + SFileDescriptor.getValidAccessModes();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "fileSize:")
  public abstract static class FileSizePrim extends UnaryExpressionNode {
    @Child ExceptionSignalingNode ioException;

    @Override
    public ExpressionNode initialize(final SourceSection sourceSection,
        final boolean eagerlyWrapped) {
      super.initialize(sourceSection, eagerlyWrapped);
      ioException = insert(ExceptionSignalingNode.createNode(new FileModule(),
          Symbols.IOException, Symbols.SIGNAL_WITH, sourceSection));
      return this;
    }

    @Specialization
    public final long getFileSize(final SFileDescriptor file) {
      return file.getFileSize(ioException);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "fileIsClosed:")
  public abstract static class FileClosedPrim extends UnaryExpressionNode {
    @Specialization
    public final boolean isClosed(final SFileDescriptor file) {
      return file.isClosed();
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "file:openIfFail:")
  public abstract static class FileOpenPrim extends BinaryExpressionNode {
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    public final Object fileOpen(final SFileDescriptor file, final SBlock handler) {
      return file.openFile(handler, dispatchHandler);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "file:readAt:ifFail:")
  public abstract static class ReadFilePrim extends TernaryExpressionNode {
    private final BranchProfile errorCases = BranchProfile.create();

    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    public final long read(final SFileDescriptor file, final long offset,
        final SBlock fail) {
      return file.read(offset, fail, dispatchHandler, errorCases);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "file:write:at:ifFail:")
  public abstract static class WriteFilePrim extends QuaternaryExpressionNode {
    private final BranchProfile errorCases = BranchProfile.create();

    @Child protected BlockDispatchNode      dispatchHandler = BlockDispatchNodeGen.create();
    @Child protected ExceptionSignalingNode ioException;

    @Override
    public ExpressionNode initialize(final SourceSection sourceSection,
        final boolean eagerlyWrapped) {
      super.initialize(sourceSection, eagerlyWrapped);
      ioException = insert(ExceptionSignalingNode.createNode(new FileModule(),
          Symbols.IOException, Symbols.SIGNAL_WITH, sourceSection));
      return this;
    }

    @Specialization
    public final Object write(final SFileDescriptor file, final long nBytes,
        final long offset, final SBlock fail) {
      file.write((int) nBytes, offset, fail, dispatchHandler, ioException, errorCases);
      return file;
    }
  }
}
