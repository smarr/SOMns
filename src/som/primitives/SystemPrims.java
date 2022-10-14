package som.primitives;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.Primitive;
import bd.primitives.Specializer;
import bd.source.SourceCoordinate;
import bd.tools.nodes.Operation;
import som.Output;
import som.VM;
import som.compiler.MixinDefinition;
import som.interop.ValueConversion.ToSomConversion;
import som.interop.ValueConversionFactory.ToSomConversionNodeGen;
import som.interpreter.Method;
import som.interpreter.Types;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.EventualMessage;
import som.interpreter.nodes.ExceptionSignalingNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.BackCacheCallNode;
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
import som.vmobjects.SClass;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;
import tools.SourceCoordinate;
import tools.asyncstacktraces.ShadowStackEntry;
import tools.asyncstacktraces.ShadowStackEntryLoad;
import tools.asyncstacktraces.StackIterator.HaltStackIterator;
import tools.concurrency.TraceParser;
import tools.concurrency.TracingActors.TracingActor;
import tools.concurrency.TracingBackend;
import tools.debugger.frontend.ApplicationThreadStack.StackFrame;
import tools.replay.actors.ActorExecutionTrace;
import tools.replay.nodes.TraceActorContextNode;
import tools.snapshot.SnapshotBackend;
import tools.snapshot.SnapshotBuffer;
import tools.snapshot.deserialization.DeserializationBuffer;


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

  @GenerateNodeFactory
  @Primitive(primitive = "traceStatistics:")
  public abstract static class TraceStatisticsPrim extends UnarySystemOperation {
    @Specialization
    @TruffleBoundary
    public final Object doSObject(final Object module) {
      long[] tracingStats = TracingBackend.getStatistics();
      long[] stats = Arrays.copyOf(tracingStats, tracingStats.length + 5);
      stats[tracingStats.length] = ShadowStackEntry.numberOfAllocations;
      ShadowStackEntry.numberOfAllocations = 0;
      stats[tracingStats.length + 1] = 0;
      stats[tracingStats.length + 2] = ShadowStackEntryLoad.cacheHit;
      ShadowStackEntryLoad.cacheHit = 0;
      stats[tracingStats.length + 3] = ShadowStackEntryLoad.megaMiss;
      ShadowStackEntryLoad.megaMiss = 0;
      stats[tracingStats.length + 4] = ShadowStackEntryLoad.megaCacheHit;
      ShadowStackEntryLoad.megaCacheHit = 0;
      return new SImmutableArray(stats, Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "hasTraceStatistics:")
  public abstract static class HasTraceStatisticsPrim extends UnarySystemOperation {
    @Specialization
    @TruffleBoundary
    public final Object doSObject(final Object module) {
      return VmSettings.UNIFORM_TRACING || VmSettings.KOMPOS_TRACING;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "memoryStatistics:")
  public abstract static class MemoryStatisticsPrim extends UnarySystemOperation {
    @Specialization
    @TruffleBoundary
    public final Object doSObject(final Object module) {
      long[] stats = TracingBackend.getMemoryStatistics();
      return new SImmutableArray(stats, Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "hasMemoryStatistics:")
  public abstract static class HasMemoryStatisticsPrim extends UnarySystemOperation {
    @Specialization
    @TruffleBoundary
    public final Object doSObject(final Object module) {
      return VmSettings.MEMORY_TRACING;
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
      File file = new File(URI.create(path).getPath());

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

  /**
   * This primitive is used be Kernel.ns and System.ns before the system is shut down with an
   * error. We use this primitive to log the messageId of the turn the error occured in.
   * The logged messageId can then be used in assisted debugging to backtrack from the error
   * to the system start, and generate breakpoints for messages on that path.
   */
  @GenerateNodeFactory
  @Primitive(primitive = "markTurnErroneous:")
  public abstract static class MarkTurnErroneousPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final Object receiver) {
      if (VmSettings.KOMPOS_TRACING) {
        EventualMessage errorMsg = EventualMessage.getCurrentExecutingMessage();
        long msgId = errorMsg.getMessageId();

        File f = new File(VmSettings.TRACE_FILE + "_errorMsgId.trace");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
          writer.write(String.valueOf(msgId));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      return receiver;
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

      Output.println("Stack Trace");

      HaltStackIterator stack = new HaltStackIterator(topNode);
      while (stack.hasNext()) {
        StackFrame frame = stack.next();

        method.add(frame.name);
        maxLengthMethod[0] = Math.max(maxLengthMethod[0], frame.name.length());
        // TODO: is this better `callNode.getEncapsulatingSourceSection();` ???

        SourceSection nodeSS = frame.section;

        if (nodeSS != null) {
          location.add(nodeSS.getSource().getName()
              + SourceCoordinate.getLocationQualifier(nodeSS));
        } else {
          location.add("");
        }
      }

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
  @Primitive(primitive = "printAsyncStackTrace:")
  public abstract static class PrintAsyncStackTracePrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final Object receiver) {
      printAsyncStackTrace(2, null);
      return receiver;
    }

    public static boolean shouldUsePreviousShadowStackEntry(final Method currentMethod,
        final Node prevExpression) {
      if (prevExpression instanceof BackCacheCallNode) {
        BackCacheCallNode ssNode =
            (BackCacheCallNode) prevExpression;
        return currentMethod == ssNode.getCachedMethod();
      }
      return true;
    }

    @TruffleBoundary
    public static void printAsyncStackTrace(final int skipDnuFrames,
        final SourceSection topNode) {

      ArrayList<String> method = new ArrayList<String>();
      ArrayList<String> location = new ArrayList<String>();
      int[] maxLengthMethod = {0};

      Output.println("Async Stack Trace");

      // First we extract Shadow Stack entry and current method from the top stack frame
      FrameInstance firstFrame = Truffle.getRuntime().getCallerFrame();
      Frame firstFrameFrame = firstFrame.getFrame(FrameAccess.READ_ONLY);
      ShadowStackEntry currentShadowStackEntry =
          (ShadowStackEntry) firstFrameFrame.getArguments()[firstFrameFrame.getArguments().length
              - 1];
      RootCallTarget ct = (RootCallTarget) firstFrame.getCallTarget();
      Method currentMethod = (Method) ct.getRootNode(); // Assumes Async stack trace printing
                                                        // cannot be called from a primitive

      // Set up Shadow Stack entry iteration: currentNode (typically cachedDispatchNode) and
      // currentShadowStackEntry
      Node currentNode;
      if (shouldUsePreviousShadowStackEntry(currentMethod,
          currentShadowStackEntry.getExpression())) {
        currentShadowStackEntry = currentShadowStackEntry.getPreviousShadowStackEntry();
        currentNode = currentShadowStackEntry.getExpression();
      } else {
        currentNode = (Node) currentMethod.getUniqueCaller();
      }

      // Main printing loop: we need to print and select each time if we
      // use the uniqueCaller or the method or the shadow stack entry data
      int debugStop = 500;
      while (currentNode != null && debugStop > 0) {
        debugStop--;
        // First we print
        currentMethod = (Method) currentNode.getRootNode();
        method.add(currentMethod.getName());
        SourceSection nodeSS = currentNode.getSourceSection();
        location.add(nodeSS.getSource().getName()
            + SourceCoordinate.getLocationQualifier(nodeSS));
        // Then we compute next node to use
        if (shouldUsePreviousShadowStackEntry(currentMethod,
            currentShadowStackEntry.getExpression())) {
          currentShadowStackEntry = currentShadowStackEntry.getPreviousShadowStackEntry();
          currentNode = currentShadowStackEntry.getExpression();
        } else {
          currentNode = (Node) currentMethod.getUniqueCaller();
        }
      }

      // Now compute the string and output it
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
    @TruffleBoundary
    public final Object doSObject(final Object receiver) {
      System.gc();
      return true;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "systemTime:")
  public abstract static class TimePrim extends UnarySystemOperation {
    @Child TraceContextNode tracer = TraceContextNodeGen.create();

    @Specialization
    public final long doSObject(final Object receiver) {
      if (VmSettings.REPLAY) {
        return vm.getTraceParser().getLongSysCallResult();
      }

      long res = System.currentTimeMillis() - startTime;
      if (VmSettings.UNIFORM_TRACING) {
        UniformExecutionTrace.longSystemCall(res, tracer);
      }
      return res;
    }

    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == BasicPrimitiveOperation.class) {
        return true;
      } else {
        return super.hasTagIgnoringEagerness(tag);
      }
    }
  }

  /**
   * This primitive serves testing purposes for the snapshot serialization by allowing to
   * serialize objects on demand.
   */
  @GenerateNodeFactory
  @Primitive(primitive = "snapshot:")
  public abstract static class SnapshotPrim extends UnaryBasicOperation {

    @Specialization
    public final Object doSObject(final Object receiver) {
      if (VmSettings.SNAPSHOTS_ENABLED) {
        SnapshotBackend.startSnapshot();
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "snapshotClone:")
  public abstract static class SnapshotClonePrim extends UnaryBasicOperation {

    @Specialization
    public final Object doSObject(final Object receiver) {
      if (VmSettings.SNAPSHOTS_ENABLED) {
        ActorProcessingThread atp =
            (ActorProcessingThread) ActorProcessingThread.currentThread();
        TracingActor ta = (TracingActor) EventualMessage.getActorCurrentMessageIsExecutionOn();
        SnapshotBuffer sb = new SnapshotBuffer(atp);
        ta.replaceSnapshotRecord();

        if (!sb.getRecord().containsObject(receiver)) {
          SClass clazz = Types.getClassOf(receiver);
          clazz.serialize(receiver, sb);
          DeserializationBuffer bb = sb.getBuffer();

          long ref = sb.getRecord().getObjectPointer(receiver);

          Object o = bb.deserialize(ref);
          assert Types.getClassOf(o) == clazz;
          return o;
        }
      }
      return Nil.nilObject;
    }
  }

  public static class IsSystemModule extends Specializer<VM, ExpressionNode, SSymbol> {
    public IsSystemModule(final Primitive prim, final NodeFactory<ExpressionNode> fact) {
      super(prim, fact);
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
  public abstract static class TicksPrim extends UnarySystemOperation implements Operation {
    @Child TraceContextNode tracer = TraceContextNodeGen.create();

    @Specialization
    public final long doSObject(final Object receiver) {
      if (VmSettings.REPLAY) {
        return vm.getTraceParser().getLongSysCallResult();
      }

      long res = System.nanoTime() / 1000L - startMicroTime;

      if (VmSettings.UNIFORM_TRACING) {
        UniformExecutionTrace.longSystemCall(res, tracer);
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

    @Override
    protected boolean hasTagIgnoringEagerness(final Class<? extends Tag> tag) {
      if (tag == BasicPrimitiveOperation.class) {
        return true;
      } else {
        return super.hasTagIgnoringEagerness(tag);
      }
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
    protected static final int INLINE_CACHE_SIZE = VmSettings.DYNAMIC_METRICS ? 100 : 6;

    @Child protected SizeAndLengthPrim size    = SizeAndLengthPrimFactory.create(null);
    @Child protected ToSomConversion   convert = ToSomConversionNodeGen.create(null);

    @Specialization(limit = "INLINE_CACHE_SIZE")
    public final Object doApply(final TruffleObject fun, final SArray args,
        @CachedLibrary("fun") final InteropLibrary interop) {
      Object[] arguments;
      if (args.isLongType()) {
        long[] arr = args.getLongStorage();
        arguments = new Object[arr.length];
        for (int i = 0; i < arr.length; i++) {
          arguments[i] = arr[i];
        }
      } else if (args.isObjectType()) {
        arguments = args.getObjectStorage();
      } else {
        CompilerDirectives.transferToInterpreter();
        throw new NotYetImplementedException();
      }

      try {
        Object result = interop.execute(fun, arguments);
        return convert.executeEvaluated(result);
      } catch (UnsupportedTypeException | ArityException
          | UnsupportedMessageException e) {
        CompilerDirectives.transferToInterpreter();
        throw new RuntimeException(e);
      }
    }
  }

  @GenerateNodeFactory
  @Primitive(primitive = "featuresSupportsExtensions:")
  public abstract static class SupportExtensionPrim extends UnarySystemOperation {
    @Specialization
    public final boolean doObject(final Object receiver) {
      return !TruffleOptions.AOT;
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
