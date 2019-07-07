package som.vmobjects;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.BranchProfile;

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

  @TruffleBoundary
  public SFileDescriptor(final String uri) {
    super(fileDescriptorClass, fileDescriptorClass.getInstanceFactory());
    f = new File(uri);
  }

  public Object openFile(final SBlock fail, final BlockDispatchNode dispatchHandler) {
    long[] storage = new long[bufferSize];
    buffer = new SMutableArray(storage, Classes.arrayClass);

    try {
      raf = open();
    } catch (FileNotFoundException e) {
      return dispatchHandler.executeDispatch(new Object[] {fail, FILE_NOT_FOUND});
    }

    return this;
  }

  @TruffleBoundary
  private RandomAccessFile open() throws FileNotFoundException {
    return new RandomAccessFile(f, accessMode.mode);
  }

  public void closeFile(final ExceptionSignalingNode ioException) {
    if (raf == null) {
      return;
    }

    try {
      closeFile();
    } catch (IOException e) {
      ioException.signal(e.getMessage());
    }
  }

  @TruffleBoundary
  private void closeFile() throws IOException {
    raf.close();
    raf = null;
  }

  public int read(final long position, final SBlock fail,
      final BlockDispatchNode dispatchHandler, final BranchProfile errorCases) {
    if (raf == null) {
      errorCases.enter();
      fail.getMethod().invoke(new Object[] {fail, FILE_IS_CLOSED});
      return 0;
    }

    if (accessMode == AccessModes.write) {
      errorCases.enter();
      fail.getMethod().invoke(new Object[] {fail, WRITE_ONLY_MODE});
      return 0;
    }

    long[] storage = buffer.getLongStorage();
    byte[] buff = new byte[bufferSize];
    int bytes = 0;

    try {
      assert raf != null;

      // set position in file
      bytes = read(position, buff);
    } catch (IOException e) {
      errorCases.enter();
      dispatchHandler.executeDispatch(new Object[] {fail, toString(e)});
    }

    // move read data to the storage
    for (int i = 0; i < bufferSize; i++) {
      storage[i] = buff[i];
    }

    return bytes;
  }

  @TruffleBoundary
  private int read(final long position, final byte[] buff) throws IOException {
    int bytes;
    raf.seek(position);
    bytes = raf.read(buff);
    return bytes;
  }

  @TruffleBoundary
  private String toString(final IOException e) {
    return e.toString();
  }

  public void write(final int nBytes, final long position, final SBlock fail,
      final BlockDispatchNode dispatchHandler, final ExceptionSignalingNode ioException,
      final BranchProfile errorCases) {
    if (raf == null) {
      errorCases.enter();
      dispatchHandler.executeDispatch(new Object[] {fail, FILE_IS_CLOSED});
      return;
    }

    if (accessMode == AccessModes.read) {
      errorCases.enter();
      fail.getMethod().invoke(new Object[] {fail, READ_ONLY_MODE});
      return;
    }

    long[] storage = buffer.getLongStorage();
    byte[] buff = new byte[bufferSize];

    for (int i = 0; i < bufferSize; i++) {
      long val = storage[i];
      if (val <= Byte.MIN_VALUE && Byte.MAX_VALUE <= val) {
        errorCases.enter();
        ioException.signal(errorMsg(val));
      }
      buff[i] = (byte) val;
    }

    try {
      write(nBytes, position, buff);
    } catch (IOException e) {
      errorCases.enter();
      dispatchHandler.executeDispatch(new Object[] {fail, toString(e)});
    }
  }

  @TruffleBoundary
  private static String errorMsg(final long val) {
    return "Buffer only supports values in the range -128 to 127 (" + val + ")";
  }

  @TruffleBoundary
  private void write(final int nBytes, final long position, final byte[] buff)
      throws IOException {
    raf.seek(position);
    raf.write(buff, 0, nBytes);
  }

  public long getFileSize(final ExceptionSignalingNode ioException) {
    try {
      return length();
    } catch (IOException e) {
      ioException.signal(e.getMessage());
    }
    return 0;
  }

  @TruffleBoundary
  private long length() throws IOException {
    return raf.length();
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
      CompilerDirectives.transferToInterpreter();
      throw new NotYetImplementedException();
    }
  }

  @TruffleBoundary
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
