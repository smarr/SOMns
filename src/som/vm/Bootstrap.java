package som.vm;

import java.io.IOException;

import som.compiler.SourcecodeCompiler;


public final class Bootstrap {

  public static void main(final String[] args) {
    assert args[0].equals("--platform");
    String platformFilename = args[1];
    try {
      SourcecodeCompiler.compileModule(".", platformFilename, null, null);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

  }

}
