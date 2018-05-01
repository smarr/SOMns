/**
 * Copyright (c) 2018 Richard Roberts, richard.andrew.roberts@gmail.com
 * Victoria University of Wellington, Wellington New Zealand
 * http://gracelang.org/applications/home/
 *
 * Copyright (c) 2013 Stefan Marr,     stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt,   michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package som.compiler;

import static som.vm.Symbols.symbolFor;

import com.oracle.truffle.api.source.Source;

import som.interpreter.SomLanguage;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.literals.IntegerLiteralNode;
import som.vm.Symbols;
import som.vmobjects.SSymbol;
import tools.language.StructuralProbe;


/**
 * This module builds SOM AST. The module categorizes its different parts:
 *
 * Objects:
 * module - creates the AST for a SOM module, via the {@link MixinBuilder} class.
 *
 */
public class AstBuilder {

  private final ScopeManager  scopeManager;
  private final SourceManager sourceManager;

  public final Objects objectBuilder;

  public AstBuilder(final Source source, final SomLanguage language,
      final StructuralProbe probe) {
    scopeManager = new ScopeManager(language, probe);
    sourceManager = new SourceManager(source);

    objectBuilder = new Objects();
  }

  public class Objects {

    /**
     * Creates the AST for a SOM module (although most of the construction is handled by the
     * {@link MixinBuilder} class).
     *
     * First the {@link MixinBuilder} is created. We then create the primary "factory", which
     * is a method on the module used to create instances of that module. And finally, we
     * create a main method that simply returns zero (via the {@link MethodBuilder} class).
     *
     * @return - the assembled class corresponding to the module
     */
    public MixinDefinition module() {
      SSymbol name = symbolFor("GraceModule");
      MixinBuilder moduleBuilder = scopeManager.newModule(name, sourceManager.empty());

      // Set up the method used to create instances
      MethodBuilder instanceFactory = moduleBuilder.getPrimaryFactoryMethodBuilder();
      instanceFactory.setSignature(Symbols.DEFAULT_MODULE_FACTORY);
      instanceFactory.addArgument(Symbols.SELF, sourceManager.empty());
      moduleBuilder.setupInitializerBasedOnPrimaryFactory(sourceManager.empty());
      moduleBuilder.setInitializerSource(sourceManager.empty());
      moduleBuilder.finalizeInitializer();

      // Set module to inherit from object
      moduleBuilder.setSimpleInheritance(Symbols.OBJECT, sourceManager.empty());

      // Create the main method, which simply returns zero
      MethodBuilder mainMethod = scopeManager.newMethod(Symbols.DEFAULT_MAIN_METHOD);
      mainMethod.addArgument(Symbols.SELF, sourceManager.empty());
      mainMethod.addArgument(Symbols.MAIN_METHOD_ARGS, sourceManager.empty());
      mainMethod.setVarsOnMethodScope();
      mainMethod.finalizeMethodScope();
      ExpressionNode zero = new IntegerLiteralNode(0).initialize(sourceManager.empty());
      scopeManager.assembleCurrentMethod(zero, sourceManager.empty());

      // Assemble and return the completed module
      return scopeManager.assumbleCurrentModule(sourceManager.empty());
    }
  }
}
