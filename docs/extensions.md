# Extensions

SOMns aims to be a general purpose language that supports a wide range of use
cases. Some applications might need to interact with the underlying system.
For instance, one might want to open network sockets, access operating
system functionality, or use Java libraries.

To avoid having to include all this functionality directly, SOMns offers a
mechanism for extensions. Such extensions can provide functionality in Java
and expose it to the language as normal Newspeak objects.

Typically, such extensions should be restricted to functionality that cannot
otherwise be realized with SOMns.

An alternative to extensions would be [Truffle's interop][0], which enables
the use of other Truffle languages from within SOMns. However, SOMns' interop
support is currently very limited.

## General Design of Extension Modules

Extension modules can be loaded the same way as normal modules and present
themselves as normal Newspeak classes. Thus, there is no visible difference
between normal modules and extension modules from the language perspective.

Let's assume we have an extension that provides support for a database.
It might be used as follows:

```Smalltalk
public loadExtensionAndConnect = (
  | (* load a .jar file instead of a .ns file *)
    moduleClass = system loadModule: 'database.jar'.
    (* instantiate the module class *)
    module = moduleClass new. |

  (* connect to database and perform a query *)
  module connectTo: 'localhost'.
  ^ module query: 'SELECT * FROM table'
)
```

To preserve the semantics of normal Newspeak classes, modules are
isolated from each other and themselves. Thus, if an extension module is loaded
multiple times, state that it maintains is kept separated.

## Creating an Extension Module

Extension modules are realized as standard JAR files, which contain a properties
file that identifies the class implementing the  [`Extension`][1] interface,
as well as a set of Truffle nodes annotated with [`@Primitive`][2].
The nodes are used to create a class, which offers them as normal methods.

As a simple example, let's implement a minimal counter primitive:

```Java
package ext;

@GenerateNodeFactory
@Primitive(primitive = "inc")
public class IncPrim extends UnaryExpressionNode {
  static long cnt = 0;

  @Specialization
  public Object inc(Object receiver) {
    cnt += 1;
    return cnt;
  }
}
```

The `IncPrim` node uses `@Primitive` to define a primitive method named `inc`.
Its implementation simply increments a counter by one.
Note that the node is a unary node, which means it takes one argument.
In Newspeak, the first argument, i.e., the receiver is always present,
but in this case it is simply ignored by the `inc(.)` method.

Note further the use of `@GenerateNodeFactory`. The annotation ensures that a
factory class is generated, which we use when creating the module class and
the methods corresponding to these primitives.

The second element we need is an implementation of `Extension`.
In this example it is simply:

```Java
package ext;

public class Extension implements som.vm.Extension {
  @Override
  public List<NodeFactory<? extends ExpressionNode>> getFactories() {
    ArrayList<NodeFactory<? extends ExpressionNode>> result = new ArrayList<>();
    result.add(IncPrimFactory.getFactory());
    return result;
  }
}
```

An extension needs to provide a list of node factories, which are referring to
node annotated with `@Primitive`.

The final element we need is the properties file.
It needs to be named `somns.extension` and be placed in the root of the JAR.
It defines which class provides the factories, and in this case it is:

```
class=ext.Extension
```

With the primitive nodes, the `Extension` class, and the `somns.extension` file,
we have everything we need in the JAR file.
To use it, as in the first example, we can use `system loadModule: 'test.jar'`.

## Example Extension and Extension Structure

For the full implementation of the example see `core-lib/TestSuite/extension`.

The general recommended structure of such an extension could be something like
the following:

```bash
build      # build directory, for jars and compiled classes
libs       # possible Java dependencies
src        # source directory for Java sources
.gitignore # ignore things like 'src_gen', which don't need to be checked into
           # the repository
build.xml  # Ant build script
src_gen    # for classes generated by the Truffle DSL
README.md  # general description and introduction to the extension
```

## Additional Dependencies

As indicated, one reason to use SOMns extensions is to make Java libraries
available to SOMns applications. Such libraries can depend themselves on other
libraries. To express such dependencies, we rely on [Java's support for JAR
classpathes][3].

Dependencies therefore can be indicated with the `Class-Path` attribute of
the `META-INF/MANIFEST.MF` file.

When assembling a JAR with Ant, the dependency could be added to the classpath
for instance like this:

```xml
<jar destfile="${build.dir}/test-extension.jar" basedir="${classes.dir}">
  <manifest>
    <attribute name="Class-Path" value="libs/extension-dep.jar" />
  </manifest>
</jar>
```



[0]: http://www.graalvm.org/docs/reference-manual/polyglot/
[1]: https://github.com/smarr/SOMns/blob/dev/src/som/vm/Extension.java
[2]: https://github.com/SOM-st/black-diamonds/blob/master/src/bd/primitives/Primitive.java
[3]: https://docs.oracle.com/javase/tutorial/deployment/jar/downman.html
