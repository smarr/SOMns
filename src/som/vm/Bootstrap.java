package som.vm;

import java.io.IOException;

import som.compiler.ClassDefinition;
import som.compiler.SourcecodeCompiler;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;


public final class Bootstrap {

  @CompilationFinal
  public static ClassDefinition platformModule;

  public static void loadPlatformModule(final String platformFile) {
    try {
      platformModule = SourcecodeCompiler.compileModule(platformFile, null, null);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public static void initializeObjectSystem() {
    // TODO Auto-generated method stub
    throw new NotYetImplementedException();
  }

  public static long executeApplication(final String appFile, final String[] args) {
    // TODO Auto-generated method stub
    throw new NotYetImplementedException();
  }
}
