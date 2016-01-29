package som.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.After;
import org.junit.Test;

import som.VM;
import som.interpreter.SomLanguage;

import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Instrument;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.tools.TruffleProfiler;


public class SomPolyglotTests {

  @After
  public void resetObjectSystem() {
    VM.resetClassReferences(true);
  }

  @Test
  public void engineKnowsSomLanguage() {
    PolyglotEngine vm = PolyglotEngine.newBuilder().build();
    assertTrue(vm.getLanguages().containsKey(SomLanguage.MIME_TYPE));
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

  @Test
  public void executeHelloWorldWithTruffleProfiler() throws IOException {
    String[] args = new String[] {"core-lib/Hello.som"};

    Builder builder = PolyglotEngine.newBuilder();
    builder.config(SomLanguage.MIME_TYPE, SomLanguage.CMD_ARGS, args);
    PolyglotEngine vm = builder.build();
    Instrument profiler = vm.getInstruments().get(TruffleProfiler.ID);
    assertTrue(profiler.isEnabled());
    Value result = vm.eval(SomLanguage.START);
    assertTrue(profiler.isEnabled());
    vm.dispose();
    assertNotNull(result);
  }

  @Test
  public void executeHelloWorldWithoutTruffleProfiler() throws IOException {
    String[] args = new String[] {"core-lib/Hello.som"};

    Builder builder = PolyglotEngine.newBuilder();
    builder.config(SomLanguage.MIME_TYPE, SomLanguage.CMD_ARGS, args);
    PolyglotEngine vm = builder.build();
    Instrument profiler = vm.getInstruments().get(TruffleProfiler.ID);
    assertTrue(profiler.isEnabled());
    profiler.setEnabled(false);
    Value result = vm.eval(SomLanguage.START);
    assertFalse(profiler.isEnabled());
    assertNotNull(result);
  }
}
