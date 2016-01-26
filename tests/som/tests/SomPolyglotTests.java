package som.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.Test;

import som.interpreter.SomLanguage;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Language;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;


public class SomPolyglotTests {

  @Test
  public void engineKnowsSomLanguage() {
    PolyglotEngine vm = PolyglotEngine.newBuilder().build();
    assertTrue(vm.getLanguages().containsKey(SomLanguage.MIME_TYPE));
  }

  @Test
  public void engineHasInstrumenter() throws IOException {
    PolyglotEngine vm = PolyglotEngine.newBuilder().build();
    InputStream in = getClass().getResourceAsStream("TruffleSomTCK.som");
    Source source = Source.fromReader(new InputStreamReader(in),
        "TruffleSomTCK.som").withMimeType(SomLanguage.MIME_TYPE);
    vm.eval(source);
    Language lang = vm.getLanguages().get(SomLanguage.MIME_TYPE);
  }

  @Test
  public void startEngineWithCommandLineParametersForHelloWorld() throws IOException {
    String[] args = new String[] {"core-lib/Hello.som"};

    Builder builder = PolyglotEngine.newBuilder();
    builder.config(SomLanguage.MIME_TYPE, SomLanguage.CMD_ARGS, args);
    PolyglotEngine vm = builder.build();
    Value result = vm.eval(SomLanguage.START);
    assertNotNull(result);
  }
}
