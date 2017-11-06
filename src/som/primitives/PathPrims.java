package som.primitives;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

import bd.primitives.Primitive;
import som.VM;
import som.interpreter.nodes.dispatch.BlockDispatchNode;
import som.interpreter.nodes.dispatch.BlockDispatchNodeGen;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Symbols;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SBlock;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject.SImmutableObject;


public final class PathPrims {
  @CompilationFinal public static SImmutableObject fileObject;

  @GenerateNodeFactory
  @ImportStatic(FilePrims.class)
  @Primitive(primitive = "fileObject:")
  public abstract static class SetFileClassPrim extends UnaryExpressionNode {
    @Specialization
    public final SImmutableObject setClass(final SImmutableObject value) {
      fileObject = value;
      return value;
    }
  }

  public static Object signalFileNotFoundException(final String fileName,
      final String message) {
    CompilerDirectives.transferToInterpreter();
    VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");

    SInvokable disp = (SInvokable) fileObject.getSOMClass().lookupPrivate(
        Symbols.symbolFor("signalFileNotFoundException:with:"),
        fileObject.getSOMClass().getMixinDefinition().getMixinId());
    return disp.invoke(new Object[] {fileObject, fileName, message});
  }

  public static Object signalIOException(final String message) {
    CompilerDirectives.transferToInterpreter();
    VM.thisMethodNeedsToBeOptimized("Should be optimized or on slowpath");

    SInvokable disp = (SInvokable) fileObject.getSOMClass().lookupPrivate(
        Symbols.symbolFor("signalIOException:"),
        fileObject.getSOMClass().getMixinDefinition().getMixinId());
    return disp.invoke(new Object[] {fileObject, message});
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathContents:")
  public abstract static class PathContentsPrim extends UnaryExpressionNode {
    @Specialization
    public final Object getContents(final String directory) {
      File f = new File(directory);
      String[] content = f.list();

      if (content == null) {
        return Nil.nilObject;
      }

      Object[] o = new Object[content.length];
      System.arraycopy(content, 0, o, 0, content.length);
      return new SImmutableArray(o, Classes.arrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "path:copyAs:ifFail:")
  public abstract static class FileCopyPrim extends TernaryExpressionNode {
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    public final Object copyAs(final String source, final String dest, final SBlock fail) {
      try {
        Files.copy(Paths.get(source), Paths.get(dest), new CopyOption[0]);
      } catch (IOException e) {
        dispatchHandler.executeDispatch(new Object[] {fail, e.toString()});
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathCreateDirectory:ifFail:")
  public abstract static class CreateDireytoryPrim extends BinaryExpressionNode {
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    public final Object createDirectory(final String dir, final SBlock fail) {
      try {
        Files.createDirectories(Paths.get(dir), new FileAttribute<?>[0]);
      } catch (IOException e) {
        dispatchHandler.executeDispatch(new Object[] {fail, e.toString()});
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathDeleteFileDir:ifFail:")
  public abstract static class DeleteDireytoryPrim extends BinaryExpressionNode {
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    public final Object delteDirectory(final String dir, final SBlock fail) {
      try {
        Files.delete(Paths.get(dir));
      } catch (IOException e) {
        dispatchHandler.executeDispatch(new Object[] {fail, e.toString()});
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathFileExists:")
  public abstract static class PathExistsPrim extends UnaryExpressionNode {
    @Specialization
    public final boolean exists(final String dir) {
      return Files.exists(Paths.get(dir), new LinkOption[0]);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathIsDirectory:")
  public abstract static class IsDirectoryPrim extends UnaryExpressionNode {
    @Specialization
    public final boolean isDirectory(final String dir) {
      return Files.isDirectory(Paths.get(dir), new LinkOption[0]);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathIsReadOnly:")
  public abstract static class IsReadOnlyPrim extends UnaryExpressionNode {
    @Specialization
    public final boolean isReadOnly(final String dir) {
      return Files.isReadable(Paths.get(dir)) && !Files.isWritable(Paths.get(dir));
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathLastModified:")
  public abstract static class LastModifiedPrim extends UnaryExpressionNode {
    @Specialization
    public final Object lastModified(final String dir) {
      try {
        return Files.getLastModifiedTime(Paths.get(dir), new LinkOption[0]).toString();
      } catch (FileNotFoundException e) {
        signalFileNotFoundException(dir, e.getMessage());
        return Nil.nilObject;
      } catch (IOException e) {
        signalIOException(e.getMessage());
        return Nil.nilObject;
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "path:moveAs:ifFail:")
  public abstract static class FileMovePrim extends TernaryExpressionNode {
    @Child protected BlockDispatchNode dispatchHandler = BlockDispatchNodeGen.create();

    @Specialization
    public final Object moveAs(final String source, final String dest, final SBlock fail) {
      try {
        Files.move(Paths.get(source), Paths.get(dest), new CopyOption[0]);
      } catch (IOException e) {
        dispatchHandler.executeDispatch(new Object[] {fail, e.toString()});
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathGetSize:")
  public abstract static class SizePrim extends UnaryExpressionNode {
    @Specialization
    public final long getSize(final String dir) {
      try {
        return Files.size(Paths.get(dir));
      } catch (FileNotFoundException e) {
        signalFileNotFoundException(dir, e.getMessage());
      } catch (IOException e) {
        signalIOException(e.getMessage());
      }
      return -1;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathCurrentDirectory:")
  public abstract static class CurrentDirectoryPrim extends UnaryExpressionNode {
    @Specialization
    public final String getSize(final Object o) {
      return System.getProperty("user.dir");
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathSeparator:")
  public abstract static class SeparatorPrim extends UnaryExpressionNode {
    @Specialization
    public final String getSeparator(final Object o) {
      return File.separator;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "pathIsAbsolute:")
  public abstract static class AbsolutePrim extends UnaryExpressionNode {
    @Specialization
    public final boolean isAbsolute(final String path) {
      Path p = Paths.get(path);
      return p.isAbsolute();
    }
  }
}
