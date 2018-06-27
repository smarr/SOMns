package som.vmobjects;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.vm.NotYetImplementedException;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SMutableArray;


public class SFileDescriptor extends SObjectWithClass {
  @CompilationFinal public static SClass fileDescriptorClass;

  private static final SSymbol FILE_NOT_FOUND  = Symbols.symbolFor("FileNotFound");
  private static final SSymbol FILE_IS_CLOSED  = Symbols.symbolFor("FileIsClosed");
  private static final SSymbol READ_ONLY_MODE  = Symbols.symbolFor("ReadOnlyMode");
  private static final SSymbol WRITE_ONLY_MODE = Symbols.symbolFor("WriteOnlyMode");

  public static final int BUFFER_SIZE = 32 * 1024;

  private SArray      buffer;
  private int         bufferSize = BUFFER_SIZE;
  private AccessModes accessMode;

  private RandomAccessFile raf;
  private final File       f;

  public static void setSOMClass(final SClass cls) {
    fileDescriptorClass = cls;
  }

  public SFileDescriptor(final String uri) {
    super(fileDescriptorClass, fileDescriptorClass.getInstanceFactory());
    f = new File(uri);
  }

  public Object openFile(final SBlock fail, final BlockDispatchNode dispatchHandler) {
    long[] storage = new long[bufferSize];
    buffer = new SMutableArray(storage, Classes.arrayClass);

    try {
      raf = new RandomAccessFile(f, accessMode.mode);
    } catch (FileNotFoundException e) {
      return dispatchHandler.executeDispatch(new Object[] {fail, FILE_NOT_FOUND});
    }

    return this;
  }

  public void closeFile(final ExceptionSignalingNode ioException) {
    if (raf == null) {
      return;
    }

    try {
      raf.close();
      raf = null;
    } catch (IOException e) {
      ioException.signal(e.getMessage());
    }
  }

  @TruffleBoundary
  public int read(final long position, final SBlock fail,
      final BlockDispatchNode dispatchHandler) {
    if (raf == null) {
      fail.getMethod().invoke(new Object[] {fail, FILE_IS_CLOSED});
      return 0;
    }

    if (accessMode == AccessModes.write) {
      fail.getMethod().invoke(new Object[] {fail, WRITE_ONLY_MODE});
      return 0;
    }

    long[] storage = (long[]) buffer.getStoragePlain();
    byte[] buff = new byte[bufferSize];
    int bytes = 0;

    try {
      assert raf != null;

      // set position in file
      raf.seek(position);
      bytes = raf.read(buff);
    } catch (IOException e) {
      dispatchHandler.executeDispatch(new Object[] {fail, e.toString()});
    }

    // move read data to the storage
    for (int i = 0; i < bufferSize; i++) {
      storage[i] = buff[i];
    }

    return bytes;
  }

  @TruffleBoundary
  public void write(final int nBytes, final long position, final SBlock fail,
      final BlockDispatchNode dispatchHandler, final ExceptionSignalingNode ioException) {
    if (raf == null) {
      dispatchHandler.executeDispatch(new Object[] {fail, FILE_IS_CLOSED});
      return;
    }

    if (accessMode == AccessModes.read) {
      fail.getMethod().invoke(new Object[] {fail, READ_ONLY_MODE});
      return;
    }

    long[] storage = (long[]) buffer.getStoragePlain();
    byte[] buff = new byte[bufferSize];

    for (int i = 0; i < bufferSize; i++) {
      if (storage[i] <= Byte.MIN_VALUE && Byte.MAX_VALUE <= storage[i]) {
        ioException.signal(
            "Buffer only supports values in the range -128 to 127 (" + storage[i] + ")");
      }
      buff[i] = (byte) storage[i];
    }

    try {
      raf.seek(position);
      raf.write(buff, 0, nBytes);
    } catch (IOException e) {
      dispatchHandler.executeDispatch(new Object[] {fail, e.toString()});
    }
  }

  public long getFileSize(final ExceptionSignalingNode ioException) {
    try {
      return raf.length();
    } catch (IOException e) {
      ioException.signal(e.getMessage());
    }
    return 0;
  }

  public boolean isClosed() {
    return raf == null;
  }

  @Override
  public boolean isValue() {
    return false;
  }

  public SArray getBuffer() {
    return buffer;
  }

  public int getBufferSize() {
    return bufferSize;
  }

  public static String getValidAccessModes() {
    return AccessModes.VALID_MODES;
  }

  public void setBufferSize(final int bufferSize) {
    // buffer size only changeable for closed files.
    if (raf == null) {
      this.bufferSize = bufferSize;
    } else {
      throw new NotYetImplementedException();
    }
  }

  public void setMode(final SSymbol mode) {
    this.accessMode = AccessModes.valueOf(mode.getString());
  }

  private enum AccessModes {
    read("r"), write("rw"), readWrite("rw");

    final String mode;

    AccessModes(final String mode) {
      this.mode = mode;
    }

    static final String VALID_MODES = renderValid();

    private static String renderValid() {
      String result = "Valid access modes are ";
      boolean first = true;
      for (AccessModes m : values()) {
        if (first) {
          first = false;
        } else {
          result += ", ";
        }
        result += "#" + m.name();
      }
      return result;
    }
  }
}
