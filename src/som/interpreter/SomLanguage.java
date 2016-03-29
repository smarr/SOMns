package som.interpreter;

import java.io.IOException;

import som.VM;
import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.MixinDefinition;
import som.compiler.Parser;
import som.compiler.Parser.ParseError;
import som.vm.NotYetImplementedException;
import som.vm.constants.Nil;
import som.vmobjects.SClass;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.FindContextNode;
import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;


@TruffleLanguage.Registration(name = "SOMns", version = "0.1.0",
                              mimeType = "application/x-newspeak-som-ns")
public final class SomLanguage extends TruffleLanguage<VM> {

  public static final String MIME_TYPE = "application/x-newspeak-som-ns";
  public static final String CMD_ARGS  = "command-line-arguments";
  public static final String FILE_EXTENSION = "som";
  public static final String DOT_FILE_EXTENSION = "." + FILE_EXTENSION;

  public static final SomLanguage INSTANCE = new SomLanguage();

  private static final class ParseResult extends RootNode {

    private final SClass moduleClass;

    ParseResult(final SClass moduleClass) {
      super(SomLanguage.class, null, null);
      this.moduleClass = moduleClass;
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      return moduleClass;
    }
  }

  @Override
  protected VM createContext(final Env env) {
    VM vm;
    try {
      vm = new VM((String[]) env.getConfig().get(CMD_ARGS), true);
    } catch (IOException e) {
      throw new RuntimeException("Failed accessing kernel or platform code of SOMns.", e);
    }
    vm.initalize();
    return vm;
  }

  public static final Source START = Source.fromNamedText(
      "", "Don't Parse, just start execution with command line arguments").withMimeType(MIME_TYPE);

  private static class StartInterpretation extends RootNode {

    private final FindContextNode<VM> contextNode;

    protected StartInterpretation() {
      super(SomLanguage.class, null, null);
      contextNode = new FindContextNode<VM>(SomLanguage.class);
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      VM vm = contextNode.executeFindContext();
      vm.execute();
      return Nil.nilObject;
    }
  }

  private CallTarget createStartCallTarget() {
    return Truffle.getRuntime().createCallTarget(new StartInterpretation());
  }

  @Override
  protected CallTarget parse(final Source code, final Node context,
      final String... argumentNames) throws IOException {
    if (code == START) {
      return createStartCallTarget();
    }

    try {
      MixinDefinition moduleDef = Parser.parseModule(code);
      ParseResult result = new ParseResult(moduleDef.instantiateModuleClass());
      return Truffle.getRuntime().createCallTarget(result);
    } catch (ParseError | MixinDefinitionError e) {
      throw new IOException(e);
    }
  }

  @Override
  protected Object findExportedSymbol(final VM context, final String globalName,
      final boolean onlyExplicit) {
    throw new NotYetImplementedException();
  }

  @Override
  protected Object getLanguageGlobal(final VM context) {
    throw new NotYetImplementedException();
  }

  @Override
  protected boolean isObjectOfLanguage(final Object object) {
    throw new NotYetImplementedException();
  }

  @Override
  protected boolean isInstrumentable(final Node node) {
    throw new NotYetImplementedException();
  }

  @Override
  protected WrapperNode createWrapperNode(final Node node) {
    throw new NotYetImplementedException();
  }

  @Override
  protected Object evalInContext(final Source source, final Node node,
      final MaterializedFrame mFrame) throws IOException {
    throw new NotYetImplementedException();
  }
}
