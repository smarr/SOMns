package som.vm;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import bd.primitives.PrimitiveLoader;
import bd.primitives.Specializer;
import som.VM;
import som.compiler.AccessModifier;
import som.compiler.MethodBuilder;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.ArgumentReadNode.LocalArgumentReadNode;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;


public final class ExtensionLoader extends PrimitiveLoader<VM, ExpressionNode, SSymbol> {

  private final EconomicMap<SSymbol, Dispatchable> primitives;

  private final String         moduleName;
  private final SomLanguage    lang;
  private final URLClassLoader moduleJar;
  private final String         extensionClassName;
  private final Extension      extension;

  public ExtensionLoader(final String moduleName, final SomLanguage lang) {
    super(Symbols.PROVIDER);
    primitives = EconomicMap.create();
    this.lang = lang;
    this.moduleName = moduleName;

    moduleJar = loadJar(moduleName);
    extensionClassName = getExtensionClassName(moduleJar);
    extension = loadExtensionClass();

    initialize();
  }

  private URLClassLoader loadJar(final String moduleName) {
    File f = new File(moduleName);
    assert f.exists() : "Tried loading extension module " + moduleName
        + " but file does not seem to exist.";

    try {
      return new URLClassLoader(new URL[] {f.toURI().toURL()},
          getClass().getClassLoader());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private String getExtensionClassName(final URLClassLoader moduleJar) {
    Properties p = new Properties();
    try {
      p.load(moduleJar.getResourceAsStream("somns.extension"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return p.getProperty("class");
  }

  private Extension loadExtensionClass() {
    Class<?> ext;
    try {
      ext = moduleJar.loadClass(extensionClassName);
      return (Extension) ext.newInstance();
    } catch (IllegalAccessException | InstantiationException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public EconomicMap<SSymbol, Dispatchable> getPrimitives() {
    return primitives;
  }

  @Override
  protected List<Specializer<VM, ExpressionNode, SSymbol>> getSpecializers() {
    return extension.getSpecializers();
  }

  @Override
  protected void registerPrimitive(
      final Specializer<VM, ExpressionNode, SSymbol> specializer) {
    String name = specializer.getPrimitive().primitive();

    assert !("".equals(name));
    SSymbol signature = Symbols.symbolFor(name);
    assert !primitives.containsKey(signature) : "clash of primitive named " + name + " in "
        + moduleName;
    primitives.put(signature,
        constructPrimitive(signature, specializer, lang));
  }

  private SInvokable constructPrimitive(final SSymbol signature,
      final Specializer<VM, ExpressionNode, SSymbol> specializer, final SomLanguage lang) {
    CompilerAsserts.neverPartOfCompilation("This is only executed during bootstrapping.");
    assert signature.getNumberOfSignatureArguments() >= 1 : "Primitives should have at least a receiver";

    // ignore the implicit vmMirror argument
    final int numArgs = signature.getNumberOfSignatureArguments();

    Source s = SomLanguage.getSyntheticSource(moduleName, specializer.getName());
    MethodBuilder prim = new MethodBuilder(true, lang, null);
    ExpressionNode[] args = new ExpressionNode[numArgs];

    SourceSection source = s.createSection(1);
    for (int i = 0; i < numArgs; i++) {
      args[i] = new LocalArgumentReadNode(true, i).initialize(source);
    }

    ExpressionNode primNode = specializer.create(null, args, source, false, lang.getVM());

    String name = moduleName + ">>" + signature.toString();

    som.interpreter.Primitive primMethodNode = new som.interpreter.Primitive(name,
        primNode, prim.getScope().getFrameDescriptor(),
        (ExpressionNode) primNode.deepCopy(), false, lang);
    return new SInvokable(signature, AccessModifier.PUBLIC, primMethodNode, null);
  }
}
