package som.tests;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.tck.LanguageProvider;
import org.graalvm.polyglot.tck.Snippet;

import som.Launcher;
import som.interpreter.SomLanguage;
import som.vm.VmOptions;


public class SomSnippets implements LanguageProvider {

  @SuppressWarnings("unused")
  void foo() {
    Builder builder = Launcher.createContextBuilder(new String[] {
        "--kernel", VmOptions.STANDARD_KERNEL_FILE,
        "--platform", VmOptions.STANDARD_PLATFORM_FILE});
    Context context = builder.build();

    InputStream in = getClass().getResourceAsStream("TruffleSomTCK.ns");
    try {
      Source source = Source.newBuilder(
          SomLanguage.LANG_ID, new InputStreamReader(in), "TruffleSomTCK.ns").build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // TODO:
    // Value tckModule = context.eval(source);
    // tckModule.SClass tck = tckModule.as(SClass.class);
    //
    // ObjectTransitionSafepoint.INSTANCE.register();
    // tck.getMixinDefinition().instantiateObject(tck, vm.getVmMirror());
    // ObjectTransitionSafepoint.INSTANCE.unregister();
  }

  @Override
  public String getId() {
    return SomLanguage.LANG_ID;
  }

  @Override
  public Value createIdentityFunction(final Context context) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<? extends Snippet> createValueConstructors(final Context context) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<? extends Snippet> createExpressions(final Context context) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<? extends Snippet> createStatements(final Context context) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<? extends Snippet> createScripts(final Context context) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<? extends Source> createInvalidSyntaxScripts(final Context context) {
    // TODO Auto-generated method stub
    return null;
  }

}
