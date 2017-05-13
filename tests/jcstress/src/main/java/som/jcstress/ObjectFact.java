package som.jcstress;

import java.io.IOException;
import java.util.Map;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;

import som.VM;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.Symbols;
import som.vm.VmOptions;
import som.vmobjects.SClass;
import som.vmobjects.SObject.SMutableObject;
import som.vmobjects.SSymbol;

public class ObjectFact {
  private final PolyglotEngine engine;
  private final SClass clazz;

  private final Dispatchable newObj;
  private final Dispatchable writeA;
  private final Dispatchable writeB;

  private final IndirectCallNode node;

  public ObjectFact() throws IOException {
    engine = getInitializedVm();
    clazz = engine.eval(SomLanguage.START).as(SClass.class);

    Map<SSymbol, Dispatchable> methods = clazz.getSOMClass().getDispatchables();

    newObj = methods.get(Symbols.symbolFor("new"));
    writeA = methods.get(Symbols.symbolFor("write:a:"));
    writeB = methods.get(Symbols.symbolFor("write:b:"));

    node = Truffle.getRuntime().createIndirectCallNode();
  }

  protected PolyglotEngine getInitializedVm() throws IOException {
    VM vm = new VM(getVmArguments(), true);
    Builder builder = vm.createPolyglotBuilder();
    PolyglotEngine engine = builder.build();

    engine.getRuntime().getInstruments().values().forEach(i -> i.setEnabled(false));
    return engine;
  }

  protected VmOptions getVmArguments() {
    return new VmOptions(new String[] {
        "--kernel",
        "/Users/smarr/Projects/SOM/SOMns/core-lib/Kernel.som",
        "--platform",
        "/Users/smarr/Projects/SOM/SOMns/tests/jcstress/src/main/resources/som/jcstress/Test.som"},
        "objClass");
  }

  public SClass getClazz() { return clazz; }

  public SMutableObject getObject() {
    return (SMutableObject) newObj.invoke(node, new Object[] {clazz});
  }

  public Dispatchable getWriteA() { return writeA; }
  public Dispatchable getWriteB() { return writeB; }

  public static void main(final String[] args) {
    try {
      ObjectFact objectFact = new ObjectFact();
      SMutableObject obj = objectFact.getObject();

      IndirectCallNode node = Truffle.getRuntime().createIndirectCallNode();
      objectFact.getWriteA().invoke(node, new Object[] {objectFact.getClazz(), obj, obj});

//      Map<SSymbol, Dispatchable> methods = obj.getSOMClass().getDispatchables();
//      Dispatchable dispA = methods.get(Symbols.symbolFor("a"));
//      Dispatchable dispB = methods.get(Symbols.symbolFor("b"));
//      System.out.println(obj);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
