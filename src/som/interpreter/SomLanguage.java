package som.interpreter;

import java.io.File;
import java.io.IOException;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags.AlwaysHalt;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

import som.Launcher;
import som.VM;
import som.compiler.MixinDefinition;
import som.vm.NotYetImplementedException;
import som.vm.VmOptions;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;
import tools.concurrency.Tags.AcquireLock;
import tools.concurrency.Tags.ActivityCreation;
import tools.concurrency.Tags.ActivityJoin;
import tools.concurrency.Tags.Atomic;
import tools.concurrency.Tags.ChannelRead;
import tools.concurrency.Tags.ChannelWrite;
import tools.concurrency.Tags.CreatePromisePair;
import tools.concurrency.Tags.EventualMessageSend;
import tools.concurrency.Tags.ExpressionBreakpoint;
import tools.concurrency.Tags.OnError;
import tools.concurrency.Tags.ReleaseLock;
import tools.concurrency.Tags.WhenResolved;
import tools.concurrency.Tags.WhenResolvedOnError;
import tools.debugger.Tags.ArgumentTag;
import tools.debugger.Tags.CommentTag;
import tools.debugger.Tags.DelimiterClosingTag;
import tools.debugger.Tags.DelimiterOpeningTag;
import tools.debugger.Tags.IdentifierTag;
import tools.debugger.Tags.KeywordTag;
import tools.debugger.Tags.LiteralTag;
import tools.debugger.Tags.LocalVariableTag;
import tools.debugger.Tags.StatementSeparatorTag;
import tools.dym.Tags.AnyNode;
import tools.dym.Tags.ArgumentExpr;
import tools.dym.Tags.ArrayRead;
import tools.dym.Tags.ArrayWrite;
import tools.dym.Tags.BasicPrimitiveOperation;
import tools.dym.Tags.CachedClosureInvoke;
import tools.dym.Tags.CachedVirtualInvoke;
import tools.dym.Tags.ClassRead;
import tools.dym.Tags.ComplexPrimitiveOperation;
import tools.dym.Tags.ControlFlowCondition;
import tools.dym.Tags.FieldRead;
import tools.dym.Tags.FieldWrite;
import tools.dym.Tags.LocalArgRead;
import tools.dym.Tags.LocalVarRead;
import tools.dym.Tags.LocalVarWrite;
import tools.dym.Tags.LoopBody;
import tools.dym.Tags.LoopNode;
import tools.dym.Tags.NewArray;
import tools.dym.Tags.NewObject;
import tools.dym.Tags.OpArithmetic;
import tools.dym.Tags.OpClosureApplication;
import tools.dym.Tags.OpComparison;
import tools.dym.Tags.OpLength;
import tools.dym.Tags.StringAccess;
import tools.dym.Tags.UnspecifiedInvoke;
import tools.dym.Tags.VirtualInvoke;
import tools.dym.Tags.VirtualInvokeReceiver;


@TruffleLanguage.Registration(id = "SOMns", name = "SOMns", version = "0.6.0",
    interactive = false, internal = false,
    characterMimeTypes = "application/x-newspeak-som-ns")
@ProvidedTags({
    RootTag.class, StatementTag.class, CallTag.class, ExpressionTag.class,

    AlwaysHalt.class,

    KeywordTag.class, LiteralTag.class,
    CommentTag.class, IdentifierTag.class, ArgumentTag.class,
    LocalVariableTag.class, StatementSeparatorTag.class,
    DelimiterOpeningTag.class, DelimiterClosingTag.class,

    UnspecifiedInvoke.class, CachedVirtualInvoke.class,
    CachedClosureInvoke.class, VirtualInvoke.class,
    VirtualInvokeReceiver.class, NewObject.class, NewArray.class,
    ControlFlowCondition.class, FieldRead.class, FieldWrite.class, ClassRead.class,
    LocalVarRead.class, LocalVarWrite.class, LocalArgRead.class, ArrayRead.class,
    ArrayWrite.class, LoopNode.class, LoopBody.class, BasicPrimitiveOperation.class,
    ComplexPrimitiveOperation.class, ArgumentExpr.class,
    StringAccess.class, OpClosureApplication.class, OpArithmetic.class,
    OpComparison.class, OpLength.class,

    EventualMessageSend.class, ChannelRead.class, ChannelWrite.class,
    ExpressionBreakpoint.class, CreatePromisePair.class, WhenResolved.class,
    WhenResolvedOnError.class, OnError.class, ActivityCreation.class,
    ActivityJoin.class, Atomic.class, AcquireLock.class, ReleaseLock.class,
    AnyNode.class
})
public final class SomLanguage extends TruffleLanguage<VM> {

  public static final String LANG_ID = "SOMns";

  public static final String START_SOURCE    = "START";
  public static final String INIT_SOURCE     = "INIT";
  public static final String SHUTDOWN_SOURCE = "SHUTDOWN";

  public static final String MIME_TYPE          = "application/x-newspeak-som-ns";
  public static final String FILE_EXTENSION     = "ns";
  public static final String DOT_FILE_EXTENSION = "." + FILE_EXTENSION;

  @Option(help = "Selector for som.tests.BasicInterpreterTests",
      category = OptionCategory.INTERNAL) //
  static final OptionKey<String> TestSelector = new OptionKey<String>("");

  @CompilationFinal private VM        vm;
  @CompilationFinal private VmOptions options;

  /** This is used by the Language Server to get to an initialized instance easily. */
  private static SomLanguage current;

  /** This is used by the Language Server to get to an initialized instance easily. */
  public static SomLanguage getCurrent() {
    return current;
  }

  public static Source getSyntheticSource(final String text, final String name) {
    return Source.newBuilder(LANG_ID, text, name).internal(true).mimeType(MIME_TYPE)
                 .build();
  }

  public static Source getSource(final File file) throws IOException {
    return Source.newBuilder(SomLanguage.LANG_ID, file.toURI().toURL()).mimeType(MIME_TYPE)
                 .build();
  }

  @Override
  protected OptionDescriptors getOptionDescriptors() {
    return new SomLanguageOptionDescriptors();
  }

  private static final class ParseResult extends RootNode {

    private final SClass moduleClass;

    ParseResult(final TruffleLanguage<?> language, final SClass moduleClass) {
      super(language, null);
      this.moduleClass = moduleClass;
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      return moduleClass;
    }
  }

  @Override
  protected VM createContext(final Env env) {
    this.options = new VmOptions(
        env.getApplicationArguments(), env.getOptions().get(TestSelector));

    if (options.isConfigUsable()) {
      vm = new VM(options);

      if (!options.isTestExecution()) {
        vm.setupInstruments(env);
      }

      return vm;
    } else {
      return null;
    }
  }

  @Override
  protected void initializeContext(final VM vm) throws Exception {
    if (vm != null) {
      vm.initalize(this);
    }
    current = this;
  }

  @Override
  protected void disposeContext(final VM context) {
    if (context != null) {
      assert vm == context;
      assert vm.isShutdown();
    }
    current = null;
  }

  public VM getVM() {
    return vm;
  }

  public static VM getVM(final Node node) {
    return getLanguage(node).getVM();
  }

  public static SomLanguage getLanguage(final Node node) {
    CompilerAsserts.neverPartOfCompilation(
        "This is a simple hack to get the VM object, and should never be on the fast path");
    return node.getRootNode().getLanguage(SomLanguage.class);
  }

  private static class StartInterpretation extends RootNode {

    private final VM vm;

    protected StartInterpretation(final SomLanguage lang) {
      super(lang, null);
      this.vm = lang.getVM();
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      if (vm == null) {
        return Launcher.EXIT_WITH_ERROR;
      }

      String selector = vm.getTestSelector();
      if (selector == null) {
        return vm.execute();
      } else {
        return vm.execute(selector);
      }
    }
  }

  private static class InitializeContext extends RootNode {
    protected InitializeContext(final SomLanguage lang) {
      super(lang, null);
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      return true;
    }
  }

  private static class ShutdownContext extends RootNode {
    private final VM vm;

    protected ShutdownContext(final SomLanguage lang) {
      super(lang, null);
      this.vm = lang.getVM();
    }

    @Override
    public Object execute(final VirtualFrame frame) {
      vm.shutdown();
      return true;
    }
  }

  @Override
  protected CallTarget parse(final ParsingRequest request) throws IOException {
    Source code = request.getSource();
    if (code.getCharacters().equals(START_SOURCE) && code.getName().equals(START_SOURCE)) {
      return Truffle.getRuntime().createCallTarget(new StartInterpretation(this));
    } else if ((code.getCharacters().equals(INIT_SOURCE)
        && code.getName().equals(INIT_SOURCE))) {
      return Truffle.getRuntime().createCallTarget(new InitializeContext(this));
    } else if ((code.getCharacters().equals(SHUTDOWN_SOURCE))
        && code.getName().equals(SHUTDOWN_SOURCE)) {
      return Truffle.getRuntime().createCallTarget(new ShutdownContext(this));
    }

    try {
      MixinDefinition moduleDef = vm.loadModule(code);
      ParseResult result = new ParseResult(this, moduleDef.instantiateModuleClass());
      return Truffle.getRuntime().createCallTarget(result);
    } catch (ThreadDeath t) {
      throw new IOException(t);
    }
  }

  @Override
  protected Object findExportedSymbol(final VM context, final String globalName,
      final boolean onlyExplicit) {
    return context.getExport(globalName);
  }

  @Override
  protected boolean isObjectOfLanguage(final Object object) {
    if (object instanceof SAbstractObject) {
      return true;
    }
    throw new NotYetImplementedException();
  }

  @Override
  protected boolean isThreadAccessAllowed(final Thread thread, final boolean singleThreaded) {
    return true;
  }
}
