Moth - A Grace on top of SOMns
==============================

Moth is an interpreter for [Grace](http://gracelang.org/) programs. It is built
on top of [SOMns](https://github.com/smarr/SOMns/) and while it achieves great
peak-performance we still have some way to go before realizing a fully
compliant Grace implementation.


Status
------

The latest release is reflected by the `master` branch [![Build
Status](https://travis-ci.com/gracelang/Moth.svg?branch=master)](https://t
ravis-ci.com/gracelang/Moth).

Although we are working toward a fully compliant Grace implementation, Moth
doesn't yet implement all of Grace's features. Nonetheless, Moth's peak
performance is comparable to [V8](https://developers.google.com/v8/) for the
AWFY benchmarks; more information can be found in our
[paper](https://arxiv.org/abs/1807.00661).

Getting Started
---------------

Moth is built on:

- [SOMns](https://github.com/richard-roberts/SOMns) - which we adapted to provide Grace support,
- [Kernan](http://gracelang.org/applications/grace-versions/kernan/) - of which we use the parser written in C#, and
- [GraceLibrary](https://github.com/richard-roberts/GraceLibrary) - a collection of Grace programs, tests, and benchmarks designed to be used in Moth.

To successfully build Kernan, you will need to have the
[xbuild](http://www.mono-project.com/docs/tools+libraries/tools/xbuild/)
installed on your machine. The best way to obtain this is to downloaded the
latest release of the [mono](https://www.mono-project.com/download/stable/) (an
umbrella project focuses on bringing better cross-platform tooling and support
to Microsoft products).

To successfully build Moth, you will need to have Apache's
[ant](https://ant.apache.org/) command line tool (easily installed through most
package managers) and
[Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html).

Building Moth
-------------

To build Moth, run our [build script](./build.xml) by invoking `ant` from
Moth's root directory. You will first see information about Kernan being built
and then SOMns (the [Grace library](./grace-lib) does not need to be compiled).
Once everything has been built successfully, you should see something like the
following output in your command line:

```sh
Buildfile: .../Moth/build.xml

compile-kernan:
    [echo] Compiling Kernan
    ...
    [exec] Build succeeded.
    [exec]      0 Warning(s)
    [exec]      0 Error(s)
    [exec]
    [exec] Time Elapsed 00:00:06.2428680

compile-somns:
     [echo] Compiling SOMns
     [echo]
     [echo]         ant.java.version: 10
     [echo]         java.version:     10.0.1
     [echo]         is.atLeastJava9:  true
     ...
     compile:
     [echo] Compiling Moth

BUILD SUCCESSFUL
Total time: 2 minutes 7 seconds
```

Provided both Kernan and Moth compiled as expected, you can now run Grace
programs using the [moth](./moth) executable:

```sh
./moth grace-lib/hello.grace
```

Note that the `moth` executable will first set the `MOTH_HOME` environment variable to Moth's root directory and then start Kernan in the background before running Moth. When Moth is finished, the executable will conclude by terminating Kernan.

Running Grace
-------------

To run a Grace program, invoke the [moth](./moth) executable from the command
line, along with the path to your program as the argument. For example,
executing `./moth grace-lib/hello.grace` runs the hello world program.

We maintain a small test suite, which can be executed via the [Test
Runner](./Tests/testRunner.grace) using `./moth -tc
GraceLibrary/Tests/testRunner.grace` (the `-tc` argument turns on dynamic
type-checking, which is required for some of the tests to pass).

Finally, you may also run Moth in benchmarking mode. To do this, execute the
[harness](./grace-lib/Benchmarks/harness.grace) along with a [Grace
benchmark](./grace-lib/Benchmarks) and the iteration numbers you want to use.
For example, executing:

```sh
./moth grace-lib/Benchmarks/harness.grace grace-lib/Benchmarks/List.grace 100 50
```


SOMns - A Simple Newspeak Implementation
========================================

Introduction
------------

Newspeak is a dynamic, class-based, object-oriented language in the
tradition of Smalltalk and Self. SOMns is an implementation of the [Newspeak
Specification Version 0.0.95][spec] derived from the [SOM][SOM] (Simple Object
Machine) class libraries, and based on [TruffleSOM][TSOM]. It is
implemented using the [Truffle framework][T] and runs on the JVM platform.

Truffle provides just-in-time compilation based on the Graal compiler,
which enables SOMns to reach [performance that is on par][AWFY] with
state-of-the-art VMs for dynamic languages, including V8.

A simple Hello World program looks like:

```Smalltalk
class Hello usingPlatform: platform = (
  public main: platform args: args = (
    'Hello World!' println.
    ^ 0
  )
)
```

Obtaining and Running SOMns
---------------------------

The basic requirements for SOMns are a system with Java 9 or later, git, ant,
and Python. Windows is currently not supported, but we test on Linux and macOS.

To checkout the code:

    git clone https://github.com/smarr/SOMns.git

Then, SOMns can be build with Ant:

    ant compile

Afterwards, the simple Hello World program is executed with:

    ./som core-lib/Hello.ns

To get an impression of the benefit o

For testing on the command line, the full command is
`./som core-lib/Benchmarks/Harness.ns Mandelbrot 500 0 500`

Additionally, there are JUnit tests and `ant test` for executing the test suite.


A more comprehensive setup guide is available in the `docs` folder and on
[ReadTheDocs][RTD].


Implementation and Deviations from the Specification
----------------------------------------------------

Compared to other Newspeaks and Smalltalks, it is completely file-based
and does not have support for images.
Instead of using customary bytecodes, SOMns is implemented as
[self-optimizing AST interpreter][SOAI] using the Truffle framework.

The overall goal is to be compliant with the specification, but include only
absolutely necessary features. The current list of intended deviations from
the specifications are as follows:

 - the mixin support of slots is not yet complete, see deactivate tests in core-lib/TestSuite/MixinTests.ns

 - simultaneous slots clauses are not fully supported (spec. 6.3.2)

 - object literals currently require a keyword prefix `objL`, to work around
   parser limitations


License and Author Information
------------------------------

This code is distributed under the MIT License. Please see the LICENSE file for
details. All contributions to the project are implicitly assumed to be under the
MIT License. If this is not desired, we ask that it is stated explicitly.
Information on previous authors are included in the AUTHORS file.

Setup Development Environment with Eclipse and VS Code
------------------------------------------------------

SOMns code is best written using our VS Code plugin, which provides support
for typical IDE features such as code navigation and compilation, as well as
a debugger. The [SOMns][vscode] support can then be installed via the Marketplace.

For the development of SOMns itself, we typically use Eclipse.
A complete guide on how to setup a workspace is available in the `docs` folder
and on [ReadTheDocs][RTD].


Development Status
------------------

Active development of SOMns happens on the `dev` branch [![Build Status](https://travis-ci.org/smarr/SOMns.png?branch=dev)](https://travis-ci.org/smarr/SOMns/tree/dev).

The latest release is reflected by the `release` branch [![Build Status](https://travis-ci.org/smarr/SOMns.png?branch=release)](https://travis-ci.org/smarr/SOMns).

Changes and releases are documented in our [CHANGELOG.md][cl].

Academic Work
-------------

SOMns is designed as platform for research with a special interest for
concurrent programming models, their interactions, and tooling for debugging.

Related papers:

 - [Transient Typechecks are (Almost) Free](https://stefan-marr.de/downloads/ecoop19-roberts-et-al-transient-typechecks-are-almost-free.pdf),
   R. Roberts, S. Marr, M. Homer, J. Noble; ECOOP'19.

 - [Efficient and Deterministic Record & Replay for Actor Languages](https://stefan-marr.de/downloads/manlang18-aumayr-et-al-efficient-and-deterministic-record-and-replay-for-actor-languages.pdf),
   D. Aumayr, S. Marr, C. Béra, E. Gonzalez Boix, H. Mössenböck; ManLang'18.

 - [Newspeak and Truffle: A Platform for Grace?](https://stefan-marr.de/downloads/grace18-marr-et-al-newspeak-and-truffle-a-platform-for-grace.pdf),
   S. Marr, R. Roberts, J. Noble; Grace'18.

 - [Few Versatile vs. Many Specialized Collections: How to design a collection library for exploratory programming?](https://stefan-marr.de/papers/px-marr-daloze-few-versatile-vs-many-specialized-collections/) S. Marr, B. Daloze; Programming Experience Workshop, PX/18.

 - [A Concurrency-Agnostic Protocol for Multi-Paradigm Concurrent Debugging Tools](https://stefan-marr.de/papers/dls-marr-et-al-concurrency-agnostic-protocol-for-debugging/),
   S. Marr, C. Torres Lopez, D. Aumayr, E. Gonzalez Boix, H. Mössenböck; Dynamic Language Symposium'17.

 - [Kómpos: A Platform for Debugging Complex Concurrent Applications](https://stefan-marr.de/downloads/progdemo-marr-et-al-kompos-a-platform-for-debugging-complex-concurrent-applications.pdf),
   S. Marr, C. Torres Lopez, D. Aumayr, E. Gonzalez Boix, H. Mössenböck; Demonstration at the &lt;Programming&gt;'17 conference.

 - [Toward Virtual Machine Adaption Rather than Reimplementation: Adapting SOMns for Grace](https://stefan-marr.de/downloads/morevms17-roberts-et-al-toward-virtual-machine-adaption.pdf),
   R. Roberts, S. Marr, M. Homer, J. Noble;
   Presentation at the MoreVMs'17 workshop at the &lt;Programming&gt;'17 conference.

 - [Optimizing Communicating Event-Loop Languages with Truffle](https://stefan-marr.de/2015/10/optimizing-communicating-event-loop-languages-with-truffle/),
    S. Marr, H. Mössenböck; Presentation at the AGERE!’15 Workshop, co-located with SPLASH’15.

 - [Cross-Language Compiler Benchmarking: Are We Fast Yet?](https://stefan-marr.de/papers/dls-marr-et-al-cross-language-compiler-benchmarking-are-we-fast-yet/)
    S. Marr, B. Daloze, H. Mössenböck at the 12th Symposium on
    Dynamic Languages co-located with SPLASH'16.

 [SOM]: http://som-st.github.io/
 [TSOM]:https://github.com/SOM-st/TruffleSOM
 [SOAI]:http://lafo.ssw.uni-linz.ac.at/papers/2012_DLS_SelfOptimizingASTInterpreters.pdf
 [T]:   http://ssw.uni-linz.ac.at/Research/Projects/JVM/Truffle.html
 [spec]:http://bracha.org/newspeak-spec.pdf
 [AWFY]:https://github.com/smarr/are-we-fast-yet
 [RTD]: http://somns.readthedocs.io/en/dev/
 [vscode]: https://marketplace.visualstudio.com/items?itemName=MetaConcProject.SOMns
 [cl]:  https://github.com/smarr/SOMns/blob/dev/CHANGELOG.md
