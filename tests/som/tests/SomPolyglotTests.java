package som.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Instrument;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.tools.TruffleProfiler;

import som.VM;
import som.interpreter.SomLanguage;


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
    VM.setEngine(vm);

    Value result = vm.eval(SomLanguage.START);
    assertNotNull(result);
  }

  @Test
  public void startEngineForTesting() throws IOException {
    Builder builder = PolyglotEngine.newBuilder();
    builder.config(SomLanguage.MIME_TYPE, SomLanguage.CMD_ARGS, new String[] {
        "--platform", "core-lib/TestSuite/BasicInterpreterTests/Arrays.som"});
    PolyglotEngine engine = builder.build();
    VM.setEngine(engine);

    engine.getInstruments().values().forEach(i -> i.setEnabled(false));

    Value v = engine.getLanguages().get(SomLanguage.MIME_TYPE).getGlobalObject();
    VM vm = (VM) v.get();
    Object result = vm.execute("testEmptyToInts");
    assertEquals((long) 3, result);
  }

  @Test
  public void executeHelloWorldWithTruffleProfiler() throws IOException {
    String[] args = new String[] {"core-lib/Hello.som"};

    Builder builder = PolyglotEngine.newBuilder();
    builder.config(SomLanguage.MIME_TYPE, SomLanguage.CMD_ARGS, args);
    PolyglotEngine vm = builder.build();
    VM.setEngine(vm);

    Instrument profiler = vm.getInstruments().get(TruffleProfiler.ID);

    Assume.assumeNotNull(profiler);
    profiler.setEnabled(true);


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
    VM.setEngine(vm);

    Instrument profiler = vm.getInstruments().get(TruffleProfiler.ID);

    Assume.assumeNotNull(profiler);

    profiler.setEnabled(true);
    assertTrue(profiler.isEnabled());
    profiler.setEnabled(false);
    Value result = vm.eval(SomLanguage.START);
    assertFalse(profiler.isEnabled());
    assertNotNull(result);
  }
}
