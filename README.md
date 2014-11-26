TruffleSOM - The Simple Object Machine Smalltalk implemented using Oracle's Truffle Framework
=============================================================================================

Introduction
------------

SOM is a minimal Smalltalk dialect used to teach VM construction at the [Hasso
Plattner Institute][SOM]. It was originally built at the University of Århus
(Denmark) where it was used for teaching and as the foundation for [Resilient
Smalltalk][RS].

In addition to TruffleSOM, other implementations exist for Java (SOM), C (CSOM),
C++ (SOM++), and Squeak/Pharo Smalltalk (AweSOM).

A simple Hello World looks like:

```Smalltalk
Hello = (
  run = (
    'Hello World!' println.
  )
)
```

This repository contains the [Truffle][T]-based implementation of SOM, including
SOM's standard library and a number of examples. Please see the [main project
page][SOM] for links to other VM implementations.

Obtaining and Running TruffleSOM
--------------------------------

To checkout the code, please note that we use git submodules. To obtain a proper
checkout, it is easiest to use a recursive clone such as:

    git clone https://github.com/SOM-st/TruffleSOM.git

Then, TruffleSOM can be build with Ant:

    ant jar

Afterwards, the tests can be executed with:

    ./som.sh -cp Smalltalk TestSuite/TestHarness.som
   
A simple Hello World program is executed with:

    ./som.sh -cp Smalltalk Examples/Hello.som

When working on TruffleSOM, for instance in Eclipse, it is helpful to download
the source files for Truffle as well:

    ant develop

Information on previous authors are included in the AUTHORS file. This code is
distributed under the MIT License. Please see the LICENSE file for details.

TruffleSOM Implementation
-------------------------

TruffleSOM implements a file-based Smalltalk with most of the language features
common to other Smalltalks. This includes support for objects, classes,
methods, closures/blocks/lambdas, non-local returns, and typical reflective
operations, e.g., method invocation or object field access.

The implementation of TruffleSOM is about 3500 lines of code in size and is a
concise but comprehensive example for how to use the Truffle framework to
implement standard language features.

Its parser creates a custom AST that is geared towards representing the
executable semantics. Thus, we did not include AST nodes that have structural
purpose only. Instead, we concentrated on the AST nodes that are relevant to
express Smalltalk language semantics.

Currently TruffleSOM demonstrates for instance:

 - method invocation: [som.interpreter.nodes.MessageSendNode](hhttps://github.com/SOM-st/TruffleSOM/blob/master/src/som/interpreter/nodes/MessageSendNode.java#L626)
 - usage of Truffle frames
 - argument passing [som.interpreter.nodes.ArgumentInitializationNode](https://github.com/SOM-st/TruffleSOM/blob/master/src/som/interpreter/nodes/ArgumentInitializationNode.java#L24)
 - associating AST tree nodes with source code, e.g., [som.compiler.Parser.unaryMessage(..)](https://github.com/smarr/TruffleSOM/blob/master/src/som/compiler/Parser.java#L652)
 - support for lexical scoping and access from nested blocks, cf.
   ContextualNode subclasses and [som.interpreter.ContextualNode.determineContext(..)](https://github.com/smarr/TruffleSOM/blob/master/src/som/interpreter/nodes/ContextualNode.java#L59)
 - usage of control-flow exception for
     - non-local returns, cf. [som.interpreter.nodes.ReturnNonLocalNode.executeGeneric(..)](https://github.com/smarr/TruffleSOM/blob/master/src/som/interpreter/nodes/ReturnNonLocalNode.java#L68)
       as well as [som.interpreter.nodes.ReturnNonLocalNode.CatchNonLocalReturnNode.executeGeneric(..)](https://github.com/SOM-st/TruffleSOM/blob/master/src/som/interpreter/nodes/ReturnNonLocalNode.java#L124)
     - looping: [som.interpreter.nodes.specialized.AbstractWhileNode](https://github.com/SOM-st/TruffleSOM/blob/master/src/som/interpreter/nodes/specialized/AbstractWhileNode.java#L62)
       as well as [som.interpreter.nodes.specialized.IntToDoMessageNode](https://github.com/SOM-st/TruffleSOM/blob/master/src/som/interpreter/nodes/specialized/IntToDoMessageNode.java#L52)


Build Status
------------

Thanks to Travis CI, all commits of this repository are tested.
The current build status is: [![Build Status](https://travis-ci.org/SOM-st/TruffleSOM.png?branch=master)](https://travis-ci.org/SOM-st/TruffleSOM)

 [SOM]: http://www.hpi.uni-potsdam.de/hirschfeld/projects/som/
 [SOMst]: https://travis-ci.org/SOM-st/
 [RS]:  http://dx.doi.org/10.1016/j.cl.2005.02.003
 [T]:   http://www.christianwimmer.at/Publications/Wuerthinger12a/
