package som.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;


public class SemanticDefinitionsReader {
  public SemanticDefinitionsReader() {
    doseFileExist();
  }

  private void doseFileExist() {
    File file = new File("SemanticPositions.json");
    try {
      if (!file.exists()) {
        file.createNewFile();
        file.deleteOnExit();
        JsonObject files = new JsonObject();

        Scanner reader = new Scanner(new File("SemanticPositions.txt"));

        // a list which contains each unique paths this will be need later when the object has
        // to be rebuild with configured tokens positions
        List<String> listOfunqiePaths = new ArrayList();

        while (reader.hasNextLine()) {

          String line = reader.nextLine();
          String[] splited = line.split("\\s+");
          // splited[0] = will be the file path
          // splited[1] = the array denoteding highlig postions e.g. [14,723,23,1,1 ....]
          if (files.has(splited[0])) {
            // this is due to platform and kenal have a second pass

              JsonElement currentValue = files.get((splited[0]));

              files.remove(splited[0]);
              files.addProperty(splited[0], currentValue.getAsString() + "," + splited[1]);

          } else {
            listOfunqiePaths.add(splited[0]);
            files.addProperty(splited[0], splited[1]);
          }

        }
        reader.close();

        JsonObject filesConfigured = new JsonObject();
        // we will now read every proberty in the files and configure the token positons to the
        // excpected output the LSP exspects
        for (int i = 0; i < listOfunqiePaths.size(); i++) {
          filesConfigured.addProperty(listOfunqiePaths.get(i),
              configuretokens(files.get(listOfunqiePaths.get(i)).toString()));
        }

        Gson gson = new GsonBuilder()
                                     .setPrettyPrinting()
                                     .create();
        FileWriter fileWriter = new FileWriter("SemanticPositions.json");
        gson.toJson(filesConfigured, fileWriter);
        fileWriter.close();

      } else {
      }
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }



  public List<Integer> getSemanticTokenDefinition(final String path)
      throws FileNotFoundException {
    Gson gson = new Gson();
    JsonReader reader = new JsonReader(new FileReader("SemanticPositions.json"));

    JsonObject files = gson.fromJson(reader, JsonObject.class);
    if (files.has(path)) {
      JsonElement positions = files.get(path);
      String test = positions.toString();
      List<String> listasstrings =
          new ArrayList<String>(
              Arrays.asList(positions.toString().replace("\"", "").split(",")));
      List<Integer> listasInts = convertStringListToIntList(
          listasstrings,
          Integer::parseInt);
      return listasInts;
    }
    List<Integer> empty = new ArrayList<>();
    return empty;
  }

  private static <T, U> List<U> convertStringListToIntList(final List<T> listOfString,
      final Function<T, U> function) {
    return listOfString.stream()
                       .map(function)
                       .collect(Collectors.toList());
  }

  private static String configuretokens(final String array) {
    List<String> listasstrings = new ArrayList<String>(Arrays.asList(array.replace("\"", "").split(",")));

    int tokenLine = Integer.parseInt(listasstrings.get(listasstrings.size() - 5));
    int tokenstart = Integer.parseInt(listasstrings.get(listasstrings.size() - 4));
    int linecount = 0;
    int startcount = 0;

    while (linecount + 5 != listasstrings.size()) {
      tokenLine = Integer.parseInt(listasstrings.get(listasstrings.size() - (5 + linecount)));
      int nextTokenLine =
          Integer.parseInt(listasstrings.get(listasstrings.size() - (10 + linecount)));
      tokenLine = tokenLine - nextTokenLine;
      if (tokenLine == 0) {
        tokenstart =
            Integer.parseInt(listasstrings.get(listasstrings.size() - (4 + startcount)));
        int nextTokenstart =
            Integer.parseInt(listasstrings.get(listasstrings.size() - (9 + startcount)));
        tokenstart = tokenstart - nextTokenstart;
      } else {
        tokenstart =
            Integer.parseInt(listasstrings.get(listasstrings.size() - (4 + startcount)));
      }
      listasstrings.set(listasstrings.size() - (5 + linecount), Integer.toString(tokenLine));
      listasstrings.set(listasstrings.size() - (4 + startcount), Integer.toString(tokenstart));
      linecount = linecount + 5;
      startcount = startcount + 5;
    }
    String newarray = listasstrings.toString().replace(" ", "");
    newarray = newarray.replace("[", "");
    return newarray.replace("]", "");
  }
}
