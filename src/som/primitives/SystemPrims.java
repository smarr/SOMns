package som.primitives;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import bd.primitives.Specializer;
import bd.tools.nodes.Operation;
import som.Output;
import som.VM;
import som.compiler.MixinDefinition;
import som.interop.ValueConversion.ToSomConversion;
import som.interop.ValueConversionFactory.ToSomConversionNodeGen;
import som.interpreter.Invokable;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.BinaryComplexOperation.BinarySystemOperation;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode.UnarySystemOperation;
import som.primitives.PathPrims.FileModule;
import som.vm.NotAFileException;
import som.vm.NotYetImplementedException;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;
import tools.SourceCoordinate;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.TraceParser;
import tools.concurrency.TracingBackend;
import tools.concurrency.nodes.TraceActorContextNode;
import tools.concurrency.nodes.TraceActorContextNodeGen;


public final class SystemPrims {

  /** File extension for SOMns extensions with Java code. */
  private static final String EXTENSION_EXT = ".jar";

  @CompilationFinal public static SObjectWithClass SystemModule;

  @GenerateNodeFactory
  @Primitive(primitive = "systemModuleObject:")
  public abstract static class SystemModuleObjectPrim extends UnaryExpressionNode {
    @Specialization
    public final Object set(final SObjectWithClass system) {
      SystemModule = system;
      return system;
    }
  }

  public static Object loadModule(final VM vm, final String path,
      final ExceptionSignalingNode ioException) {
    // TODO: a single node for the different exceptions?
    try {
      if (path.endsWith(EXTENSION_EXT)) {
        return vm.loadExtensionModule(path);
      } else {
        MixinDefinition module = vm.loadModule(path);
        return module.instantiateModuleClass();
      }
    } catch (FileNotFoundException e) {
      ioException.signal(path, "Could not find module file. " + e.getMessage());
    } catch (NotAFileException e) {
      ioException.signal(path, "Path does not seem to be a file. " + e.getMessage());
    } catch (IOException e) {
      ioException.signal(e.getMessage());
    }
    assert false : "This should never be reached, because exceptions do not return";
    return Nil.nilObject;
  }

  @GenerateNodeFactory
  @Primitive(primitive = "load:")
  public abstract static class LoadPrim extends UnarySystemOperation {
    @Child ExceptionSignalingNode ioException;

    @Override
    public UnarySystemOperation initialize(final VM vm) {
      super.initialize(vm);
      ioException = insert(ExceptionSignalingNode.createNode(new FileModule(),
          Symbols.IOException, Symbols.SIGNAL_WITH, sourceSection));
      return this;
    }

    @Specialization
    @TruffleBoundary
    public final Object doSObject(final String moduleName) {
      return loadModule(vm, moduleName, ioException);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "load:nextTo:")
  public abstract static class LoadNextToPrim extends BinarySystemOperation {
    protected @Child ExceptionSignalingNode ioException;

    @Override
    public BinarySystemOperation initialize(final VM vm) {
      super.initialize(vm);
      ioException = insert(ExceptionSignalingNode.createNode(new FileModule(),
          Symbols.IOException, Symbols.SIGNAL_WITH, sourceSection));
      return this;
    }

    @Specialization
    @TruffleBoundary
    public final Object load(final String filename, final SObjectWithClass moduleObj) {
      String path = moduleObj.getSOMClass().getMixinDefinition().getSourceSection().getSource()
                             .getPath();
      File file = new File(path);

      return loadModule(vm, file.getParent() + File.separator + filename, ioException);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "exit:")
  public abstract static class ExitPrim extends UnarySystemOperation {
    @Specialization
    @TruffleBoundary
    public final Object doSObject(final long error) {
      vm.requestExit((int) error);
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "printString:")
  public abstract static class PrintStringPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final String argument) {
      Output.print(argument);
      return argument;
    }

    @Specialization
    public final Object doSObject(final SSymbol argument) {
      return doSObject(argument.getString());
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "printNewline:")
  public abstract static class PrintInclNewlinePrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final String argument) {
      Output.println(argument);
      return argument;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "printWarning:")
  public abstract static class PrintWarningPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final String argument) {
      Output.warningPrintln(argument);
      return argument;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "printError:")
  public abstract static class PrintErrorPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final String argument) {
      Output.errorPrintln(argument);
      return argument;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "printStackTrace:")
  public abstract static class PrintStackTracePrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final Object receiver) {
      printStackTrace(2, null);
      return receiver;
    }

    @TruffleBoundary
    public static void printStackTrace(final int skipDnuFrames, final SourceSection topNode) {
      ArrayList<String> method = new ArrayList<String>();
      ArrayList<String> location = new ArrayList<String>();
      int[] maxLengthMethod = {0};
      boolean[] first = {true};
      Output.println("Stack Trace");

      Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
        @Override
        public Object visitFrame(final FrameInstance frameInstance) {
          RootCallTarget ct = (RootCallTarget) frameInstance.getCallTarget();

          // TODO: do we need to handle other kinds of root nodes?
          if (!(ct.getRootNode() instanceof Invokable)) {
            return null;
          }

          Invokable m = (Invokable) ct.getRootNode();

          String id = m.getName();
          method.add(id);
          maxLengthMethod[0] = Math.max(maxLengthMethod[0], id.length());
          Node callNode = frameInstance.getCallNode();
          if (callNode != null || first[0]) {
            SourceSection nodeSS;
            if (first[0]) {
              first[0] = false;
              nodeSS = topNode;
            } else {
              nodeSS = callNode.getEncapsulatingSourceSection();
            }
            if (nodeSS != null) {
              location.add(nodeSS.getSource().getName()
                  + SourceCoordinate.getLocationQualifier(nodeSS));
            } else {
              location.add("");
            }
          } else {
            location.add("");
          }

          return null;
        }
      });

      StringBuilder sb = new StringBuilder();
      for (int i = method.size() - 1; i >= skipDnuFrames; i--) {
        sb.append(String.format("\t%1$-" + (maxLengthMethod[0] + 4) + "s",
            method.get(i)));
        sb.append(location.get(i));
        sb.append('\n');
      }

      Output.print(sb.toString());
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "vmArguments:")
  public abstract static class VMArgumentsPrim extends UnarySystemOperation {
    @Specialization
    public final SImmutableArray getArguments(final Object receiver) {
      return new SImmutableArray(vm.getArguments(),
          Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "systemGC:")
  public abstract static class FullGCPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final Object receiver) {
      System.gc();
      return true;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "systemTime:")
  public abstract static class TimePrim extends UnaryBasicOperation {
    @Child TraceActorContextNode tracer = TraceActorContextNodeGen.create();

    @Specialization
    public final long doSObject(final Object receiver) {
      if (VmSettings.REPLAY) {
        return TraceParser.getLongSysCallResult();
      }

      long res = System.currentTimeMillis() - startTime;
      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.longSystemCall(res, tracer);
      }
      return res;
    }
  }

  public static class IsSystemModule extends Specializer<VM, ExpressionNode, SSymbol> {
    public IsSystemModule(final Primitive prim, final NodeFactory<ExpressionNode> fact,
        final VM vm) {
      super(prim, fact, vm);
    }

    @Override
    public boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      // XXX: this is the case when doing parse-time specialization
      if (args == null) {
        return true;
      }
      return args[0] == SystemPrims.SystemModule;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "systemTicks:", selector = "ticks",
      specializer = IsSystemModule.class, noWrapper = true)
  public abstract static class TicksPrim extends UnaryBasicOperation implements Operation {
    @Child TraceActorContextNode tracer = TraceActorContextNodeGen.create();

    @Specialization
    public final long doSObject(final Object receiver) {
      if (VmSettings.REPLAY) {
        return TraceParser.getLongSysCallResult();
      }

      long res = System.nanoTime() / 1000L - startMicroTime;

      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.longSystemCall(res, tracer);
      }
      return res;
    }

    @Override
    public String getOperation() {
      return "ticks";
    }

    @Override
    public int getNumArguments() {
      return 1;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "systemExport:as:")
  public abstract static class ExportAsPrim extends BinarySystemOperation {
    @Specialization
    public final boolean doString(final Object obj, final String name) {
      vm.registerExport(name, obj);
      return true;
    }

    @Specialization
    public final boolean doSymbol(final Object obj, final SSymbol name) {
      return doString(obj, name.getString());
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "systemApply:with:")
  public abstract static class ApplyWithPrim extends BinaryComplexOperation {
    private final ValueProfile storageType = ValueProfile.createClassProfile();

    @Child protected SizeAndLengthPrim size    = SizeAndLengthPrimFactory.create(null);
    @Child protected ToSomConversion   convert = ToSomConversionNodeGen.create(null);

    @Specialization
    public final Object doApply(final TruffleObject fun, final SArray args) {
      Node execNode = Message.createExecute((int) size.executeEvaluated(args)).createNode();

      Object[] arguments;
      if (args.isLongType()) {
        long[] arr = args.getLongStorage(storageType);
        arguments = new Object[arr.length];
        for (int i = 0; i < arr.length; i++) {
          arguments[i] = arr[i];
        }
      } else if (args.isObjectType()) {
        arguments = args.getObjectStorage(storageType);
      } else {
        CompilerDirectives.transferToInterpreter();
        throw new NotYetImplementedException();
      }

      try {
        Object result = ForeignAccess.sendExecute(execNode, fun, arguments);
        return convert.executeEvaluated(result);
      } catch (UnsupportedTypeException | ArityException
          | UnsupportedMessageException e) {
        CompilerDirectives.transferToInterpreter();
        throw new RuntimeException(e);
      }
    }
  }

  static {
    long current = System.nanoTime() / 1000L;
    startMicroTime = current;
    startTime = current / 1000L;
  }
  private static long startTime;
  private static long startMicroTime;
}
