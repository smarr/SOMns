package som.vmobjects;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.primitives.PathPrims;
import som.vm.constants.Classes;
import som.vmobjects.SArray.SMutableArray;


public class SFileDescriptor extends SObjectWithClass {
  @CompilationFinal public static SClass fileDescriptorClass;

  public static final int BUFFER_SIZE = 32 * 1024;

  private boolean     open       = false;
  private SArray      buffer;
  private SSymbol     mode;
  private int         bufferSize = BUFFER_SIZE;
  private AccessModes access;

  private RandomAccessFile raf;
  private final File       f;

  public static void setSOMClass(final SClass cls) {
    fileDescriptorClass = cls;
  }

  public SFileDescriptor(final String uri) {
    super(fileDescriptorClass, fileDescriptorClass.getInstanceFactory());
    f = new File(uri);
  }

  public void openFile(final SBlock fail, final BlockDispatchNode dispatchHandler) {
    long[] storage = new long[bufferSize];
    buffer = new SMutableArray(storage, Classes.arrayClass);

    try {
      this.access = AccessModes.valueOf(mode.getString().toUpperCase());
    } catch (Exception e) {
      dispatchHandler.executeDispatch(new Object[] {fail, "invalid access mode: " + mode});
    }

    try {
      raf = new RandomAccessFile(f, access.getMode());
    } catch (FileNotFoundException e) {
      dispatchHandler.executeDispatch(new Object[] {fail, e.toString()});
    }

    open = true;
  }

  public void closeFile() {
    try {
      raf.close();
      open = false;
    } catch (IOException e) {
      PathPrims.signalIOException(e.getMessage());
    }
  }

  public int read(final long position, final SBlock fail,
      final BlockDispatchNode dispatchHandler) {
    if (!open) {
      fail.getMethod().invoke(new Object[] {fail, "File not open"});
      return 0;
    }

    if (access == AccessModes.WRITE) {
      fail.getMethod().invoke(new Object[] {fail, "Opened in write only"});
      return 0;
    }

    long[] storage = (long[]) buffer.getStoragePlain();
    byte[] buff = new byte[bufferSize];
    int bytes = 0;

    try {
      assert open;

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

  public void write(final int nBytes, final long position, final SBlock fail,
      final BlockDispatchNode dispatchHandler) {
    if (!open) {
      dispatchHandler.executeDispatch(new Object[] {fail, "File not opened"});
      return;
    }

    if (access == AccessModes.READ) {
      fail.getMethod().invoke(new Object[] {fail, "Opened in read only"});
      return;
    }

    long[] storage = (long[]) buffer.getStoragePlain();
    byte[] buff = new byte[bufferSize];

    for (int i = 0; i < bufferSize; i++) {
      if (storage[i] <= Byte.MIN_VALUE && Byte.MAX_VALUE <= storage[i]) {
        PathPrims.signalIOException(
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

  public long getFileSize() {
    try {
      return raf.length();
    } catch (FileNotFoundException e) {
      PathPrims.signalFileNotFoundException(f.getAbsolutePath(), e.getMessage());
    } catch (IOException e) {
      PathPrims.signalIOException(e.getMessage());
    }
    return 0;
  }

  public boolean isClosed() {
    return !open;
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

  public void setBufferSize(final int bufferSize) {
    // buffer size only changeable for closed files.
    if (!open) {
      this.bufferSize = bufferSize;
    }
  }

  public void setMode(final SSymbol mode) {
    this.mode = mode;
  }

  private enum AccessModes {
    READ("r"), WRITE("rw"), READWRITE("rw");
    String mode;

    AccessModes(final String mode) {
      this.mode = mode;
    }

    String getMode() {
      return mode;
    }
  }
}
