SOMns - A Newspeak for Concurrency Research
===========================================

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

SOMns is built and maintained with the goal to facilitate research onf
concurrency models, their safe interactions, and tooling to make it possible to
build more correct systems more easily.

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
and Python. Windows is not yet fully supported. SOMns is tested on Linux and macOS.

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


License, Author Information, Citation
-------------------------------------

This code is distributed under the MIT License. Please see the LICENSE file for
details. All contributions to the project are implicitly assumed to be under the
MIT License. If this is not desired, we ask that it is stated explicitly.
Information on previous authors are included in the AUTHORS file.

If you use SOMns for your research, please cite it is as follows:

> Stefan Marr et al. SOMns: A Newspeak for Concurrency Research. https://doi.org/10.5281/zenodo.3270908 

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.3270908.svg)](https://doi.org/10.5281/zenodo.3270908)

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
