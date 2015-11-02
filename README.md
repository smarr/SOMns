SOMns - A Simple Newspeak Implementation
========================================

Introduction
------------

Newspeak is a dynamic, class-based, purely object-oriented language in the
tradition of Smalltalk and Self. SOMns is an implementation of the [Newspeak
Specification Version 0.0.95][spec] derived from the [SOM][SOM](Simple Object
Machine) class libraries, and based on the [TruffleSOM][TSOM]. Thus, SOMns is
implemented using the [Truffle framework][T] and runs on the JVM platform.

A simple Hello World program looks like:

```Smalltalk
class Hello usingPlatform: platform = (
  public main: platform args: args = (
    'Hello World!' println.
    ^ 0
  )
)
```

Implementation and Deviations from the Specification
----------------------------------------------------

SOMns is implemented as [self-optimizing AST interpreter][SOAI] using the
Truffle framework. Thus, it can utilize the Truffle support for just-in-time
compilation to optimize the execution performance at runtime. It is completely
file-based and does not have support for images. The parser is written in Java
and creates a custom AST that is geared towards representing the executable
semantics.

The overall goal is to be compliant with the specification, but include only
absolutely necessary features. The current list of intended deviations from
the specifications are as follows:

 - the mixin support of slots is not yet complete, see deactivate tests in core-lib/TestSuite/MixinTests.som

 - simultaneous slots clauses are not supported (spec. 6.3.2)

 - the file syntax is adapted to be more practical. This includes that
   category names are optional instead of being mandatory.

 - setter send syntax is still based on the classic Smalltalk `:=`
 
 - local variables in methods do not yet support the full slotDeclartion style
 
 - as in SOM method chains are not supported

 - as in SOM, blocks can only have 3 arguments (counting `self`)

Obtaining and Running SOMns
--------------------------------

To checkout the code:

    git clone https://github.com/smarr/SOMns.git

Then, TruffleSOM can be build with Ant:

    ant jar

Afterwards, the simple Hello World program is executed with:

    ./som.sh core-lib/Hello.som

Information on previous authors are included in the AUTHORS file. This code is
distributed under the MIT License. Please see the LICENSE file for details.


Build Status
------------

The current build status is: [![Build Status](https://travis-ci.org/smarr/SOMns.png?branch=master)](https://travis-ci.org/smarr/SOMns)

Academic Work
-------------

SOMns is designed as platform for research on concurrent programming models,
and their interactions. Here, we collect related papers:

 - [Optimizing Communicating Event-Loop Languages with
    Truffle](http://stefan-marr.de/2015/10/optimizing-communicating-event-loop-languages-with-truffle/),
    Stefan Marr, Hanspeter Mössenböck; Presentation at the AGERE!’15 Workshop, co-located with SPLASH’15.


 [SOM]: http://som-st.github.io/
 [TSOM]:https://github.com/SOM-st/TruffleSOM
 [SOAI]:http://lafo.ssw.uni-linz.ac.at/papers/2012_DLS_SelfOptimizingASTInterpreters.pdf
 [T]:   http://ssw.uni-linz.ac.at/Research/Projects/JVM/Truffle.html
 [spec]:http://bracha.org/newspeak-spec.pdf
