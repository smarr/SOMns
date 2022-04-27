package som.compiler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

import com.oracle.truffle.api.source.SourceSection;

import bd.source.SourceCoordinate;


public class SemanticDefinitionWriter {
  public SemanticDefinitionWriter() {
    doseFileExist();
  }

  private void doseFileExist() {
    File file = new File("SemanticPositions.txt");
    try {
      if (!file.exists()) {
        file.createNewFile();
        file.deleteOnExit();
      } else {
      }
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void storeSemanticPosition(final String mixinName, final SourceCoordinate coord,
      final SourceSection soruceSection, final String type)
      throws FileNotFoundException, IOException {
    try {
      String path = soruceSection.getSource().getPath();
      if (path == null) {
        path = soruceSection.getSource().getName();
      }
      // the platform and kenall have many parses and atm the only soultion i can use is to
      // just not write them to the file.
      if (path.equals(
          "/home/hburchell/vscode/SOMns-vscode/server/libs/SOMns/core-lib/Platform.ns")
          || path.equals(
              "/home/hburchell/vscode/SOMns-vscode/server/libs/SOMns/core-lib/Kernel.ns")) {
        return;
      }
      FileWriter writer = new FileWriter("SemanticPositions.txt", true);
      BufferedWriter out = new BufferedWriter(writer);
      out.write("file://" + path + " "
          + String.format("%d,%d,%d,%d,%d", coord.startLine - 1, coord.startColumn - 1,
              mixinName.length(), getEncodedTokenType(type), 0));
      out.newLine();
      out.close();

    } catch (IOException e) {

      e.printStackTrace();
    }

  }

  public void methodStoreSemanticPosition(final String mixinName, final SourceCoordinate coord,
      final SourceSection soruceSection, final String type, final String accessModifyer)
      throws FileNotFoundException, IOException {
    try {
      String path = soruceSection.getSource().getPath();
      if (path == null) {
        path = soruceSection.getSource().getName();
      }
      // the platform and kenall have many parses and atm the only soultion i can use is to
      // just not write them to the file.
      if (path.equals(
          "/home/hburchell/vscode/SOMns-vscode/server/libs/SOMns/core-lib/Platform.ns")
          || path.equals(
              "/home/hburchell/vscode/SOMns-vscode/server/libs/SOMns/core-lib/Kernel.ns")) {
        return;
      }
      FileWriter writer = new FileWriter("SemanticPositions.txt", true);
      BufferedWriter out = new BufferedWriter(writer);
      out.write("file://" + path + " "
          + String.format("%d,%d,%d,%d,%d", coord.startLine - 1,
              coord.startColumn + (accessModifyer.length()),
              mixinName.length(), getEncodedTokenType(type), 0));
      out.newLine();
      out.close();

    } catch (IOException e) {

      e.printStackTrace();
    }

  }

  private int getEncodedTokenType(final String TokenType) {
    int intToReturn = 0;
    switch (TokenType.toLowerCase()) {
      case "class":
        intToReturn = 0;
        break;
      case "method":
        intToReturn = 1;
        break;

      default:
        break;
    }
    return intToReturn;
  }

  private int getEncodedTokenModifier(final String TokenModifier) {
    int intToReturn = 0;
    switch (TokenModifier.toLowerCase()) {
      case "public":
        intToReturn = 1;
        break;
      case "private":
        intToReturn = 2;
        break;

      default:
        break;
    }
    return intToReturn;
  }

}
