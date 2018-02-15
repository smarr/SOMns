package som.primitives;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.QuaternaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySystemOperation;
import som.primitives.actors.PromisePrims;
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
    @Specialization
    public final Object closeFile(final SFileDescriptor file) {
      file.closeFile();
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
    @Specialization
    public final Object setModeSymbol(final SFileDescriptor file, final SSymbol mode) {
      file.setMode(mode);
      return file;
    }

    @Fallback
    public final Object setWithUnsupportedValue(final Object file, final Object mode) {
      SFileDescriptor.signalInvalidAccessMode(mode);
      return file;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "fileSize:")
  public abstract static class FileSizePrim extends UnaryExpressionNode {
    @Specialization
    public final long getFileSize(final SFileDescriptor file) {
      return file.getFileSize();
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
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    public final long read(final SFileDescriptor file, final long offset,
        final SBlock fail) {
      return file.read(offset, fail, dispatchHandler);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "file:write:at:ifFail:")
  public abstract static class WriteFilePrim extends QuaternaryExpressionNode {
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    public final Object write(final SFileDescriptor file, final long nBytes,
        final long offset, final SBlock fail) {
      file.write((int) nBytes, offset, fail, dispatchHandler);
      return file;
    }
  }
}
