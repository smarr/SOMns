# Change Log

## [Unreleased] \(0.7.0\) - unnamed [v7]

  -

## [0.6.0] - Black Diamonds [v6]

The main goal of this release was to move more common code to the
[Black Diamonds project][bd]. However, since this took a long time, many other
changes and improvements accumulated.

One of the most important changes for users is support for Java 9 and 10, which
simplifies using SOMns drastically, because users do not need a custom Java 8 VM
any longer.

Another important user-facing change is the inclusion of our documentation into
the repository and its deployment to [somns.readthedocs.io][docs].

### Black Diamonds

  - Move eager primitive handling to BD                     [\#185](https://github.com/smarr/SOMns/pull/185)

  - Move AST inlining and splitting to BD                   [\#231](https://github.com/smarr/SOMns/pull/231),
                                                            [\#193](https://github.com/smarr/SOMns/pull/193),
                                                            [\#92](https://github.com/smarr/SOMns/pull/92)

### Major Improvements

  - Simplify setup and use of Graal by supporting Java 9/10   [\#232](https://github.com/smarr/SOMns/pull/232),
                                                              [\#242](https://github.com/smarr/SOMns/pull/242)

  - Included [documentation][docs] into repo, and improve it  [\#212](https://github.com/smarr/SOMns/pull/212),
                                                              [\#222](https://github.com/smarr/SOMns/pull/222)

  - Adopt Files and Streams libraries from Newspeak           [\#220](https://github.com/smarr/SOMns/pull/220),
                                                              [\#204](https://github.com/smarr/SOMns/pull/204),
                                                              [\#190](https://github.com/smarr/SOMns/pull/190)

  - Add support for Unicode codepoints to strings             [\#217](https://github.com/smarr/SOMns/pull/217)

  - Support n-ary Blocks, be strict, add `#cull:*`            [\#215](https://github.com/smarr/SOMns/pull/215),
                                                              [\#115](https://github.com/smarr/SOMns/pull/115),
                                                              [\#94](https://github.com/smarr/SOMns/pull/94),
                                                              [\#21](https://github.com/smarr/SOMns/pull/21)

### Added Benchmarks

  - Added classic tinyBenchmarks known from Smalltalks        [\#230](https://github.com/smarr/SOMns/pull/230)

  - Add fork/join benchmarks                                  [\#209](https://github.com/smarr/SOMns/pull/209)

### New Interpreter Tooling

  - Add tool to identify candidates for super-instructions    [\#192](https://github.com/smarr/SOMns/pull/192)

### General Maintenance

  - Fix outer traversal to use lexical chain                  [\#238](https://github.com/smarr/SOMns/pull/238)

  - Reduce race-related stack overflows, and handled invalid layouts in ClassSlotAccessNode
                                                              [\#244](https://github.com/smarr/SOMns/pull/244)
  - Adopt Economic collections of GraalVM to ensure insertion order
                                                              [\#243](https://github.com/smarr/SOMns/pull/243)

  - Handle method/mixin scope uniformly                       [\#233](https://github.com/smarr/SOMns/pull/233)

  - Throw a proper SOMns Exception when a module isn't found  [\#229](https://github.com/smarr/SOMns/pull/229)

  - Update Truffle to latest version                          [\#225](https://github.com/smarr/SOMns/pull/225),
                                                              [\#210](https://github.com/smarr/SOMns/pull/210),
                                                              [\#194](https://github.com/smarr/SOMns/pull/194),
                                                              [\#186](https://github.com/smarr/SOMns/pull/186)

  - Colorizing terminal output                                [\#224](https://github.com/smarr/SOMns/pull/224)

  - Start tracking Java coverage                              [\#221](https://github.com/smarr/SOMns/pull/221)

  - Make Kompos tests more robust and less racy               [\#206](https://github.com/smarr/SOMns/pull/212),
                                                              [\#205](https://github.com/smarr/SOMns/pull/205)

  - Improvement debugger, transmitting partial arrays         [\#200](https://github.com/smarr/SOMns/pull/200),
                                                              [\#199](https://github.com/smarr/SOMns/pull/199)

  - Updated Kompos dependencies                               [\#207](https://github.com/smarr/SOMns/pull/207),
                                                              [\#200](https://github.com/smarr/SOMns/pull/200)

  - Improve accessibility for Language Server                 [\#195](https://github.com/smarr/SOMns/pull/195)

  - Replace node constructors with explicit initialization    [\#184](https://github.com/smarr/SOMns/pull/184)

### Bug Fixes

  - Fixed empty Array literals causing ArrayIndexOutOfBounds  [\#216](https://github.com/smarr/SOMns/pull/216)

  - Fixed mixin initializers                                  [\#201](https://github.com/smarr/SOMns/pull/201)

  - Fix VM exit and handle System>>#exit: for async apps      [\#203](https://github.com/smarr/SOMns/pull/203)
                                                              [\#191](https://github.com/smarr/SOMns/pull/191)

  - Outer sends need to check both super classes and mixins   [\#189](https://github.com/smarr/SOMns/pull/189),
                                                              [\#9](https://github.com/smarr/SOMns/pull/9)

  - Fix bug in core-lib/Benchmarks/All.ns and improve DyM     [\#188](https://github.com/smarr/SOMns/pull/188)


## [0.5.0] - More Newspeak [v5]

The main focus of this release is to improve compliance with the Newspeak
specification. Specifically, we added various language features to the parser
to be able to parse the Newspeak benchmarks and main repository with only minor
changes.

### Newspeak Compatibility Improvements

 - Implement Newspeak setter send syntax and remove old assignment syntax [\#7](https://github.com/smarr/SOMns/issues/7), [\#170](https://github.com/smarr/SOMns/pull/170)

 - Added support for Newspeak's full numeral syntax. This includes notation for
   a radix and the exponent notation. Examples: 16rFFFF, 2r10.11, 3.7e3 [\#172](https://github.com/smarr/SOMns/pull/172)

 - Added support for object literals. Because of parsing issues, we currently
   use the keyword `objL` to identify literals. Otherwise, they are mostly
   compliant the Newspeak specification.
   [#86](https://github.com/smarr/SOMns/issues/86), [\#112](https://github.com/smarr/SOMns/pull/112)

 - Add support for message cascades [\#18](https://github.com/smarr/SOMns/issues/18), [\#176](https://github.com/smarr/SOMns/pull/176), [\#178](https://github.com/smarr/SOMns/pull/178)

 - Add Newspeak type annotation support and local variable initializer expressions [\#175](https://github.com/smarr/SOMns/pull/175)

 - Add array literals [\#173](https://github.com/smarr/SOMns/pull/173)

 - Change standard file extension to `.ns` [\#181](https://github.com/smarr/SOMns/pull/181)

 - Various improvements in [\#178](https://github.com/smarr/SOMns/pull/178)
   - Support single quotes in strings as escape sequence
   - Support literal characters
   - Parse simultaneous slot definitions, but don't handle them yet.
   - Support methods named `class`

### Changes for Development Setup

  - Added automatic formatting for Java code [\#131](https://github.com/smarr/SOMns/pull/131)

  - Added automatic formatting of TypeScript code [\#167](https://github.com/smarr/SOMns/pull/167)

### General Maintenance

  - Fix handling of multiple stepping threads in Truffle [\#168](https://github.com/smarr/SOMns/pull/168)

  - Ensure eager primitives get correct parent node set, and minor cleanup [\#171](https://github.com/smarr/SOMns/pull/171)

  - Avoid block splitting when not necessary [\#177](https://github.com/smarr/SOMns/pull/177)

  - Model promise BPs solely with onResolver and onResolution [\#169](https://github.com/smarr/SOMns/pull/169)

  - Fix turn stepping operations, and introduce better actor testing framework [\#179](https://github.com/smarr/SOMns/pull/179)

  - Update Truffle to latest version \(\>0.26\) [\#163](https://github.com/smarr/SOMns/pull/163)

  - Add missing `@TruffleBoundaries` for SubstrateVM [\#165](https://github.com/smarr/SOMns/pull/165)

  - Simplify Object Model and add StorageAccessor [\#164](https://github.com/smarr/SOMns/pull/164)

## [0.4.0] - [Concurrency-Agnostic Debugger][v4]

This release introduces concurrency-agnostic debugging based on Kómpos.
It is realized by using a debugger protocol that abstracts from concurrency
concepts and instead uses a uniform representation and meta data that instructs
Kómpos how to understand and visualize breakpoints, stepping operations, and
data visualization

  - introduced a uniform trace format ([\#155](https://github.com/smarr/SOMns/pull/155))

  - added process view

  - refactor handling of breakpoints and stepping in interpreter and Kómpos

  - added advanced stepping operations and breakpoints for STM, fork/join, actors, CSP, threads and locks

### Other Enhancements

  - Switch to unified Truffle+Graal repo [\#149](https://github.com/smarr/SOMns/pull/149)

  - Updated to Truffle 0.25 [\#132](https://github.com/smarr/SOMns/pull/132)

  - Use precise array type check [\#128](https://github.com/smarr/SOMns/pull/128)

  - Make Kómpos tests more robust, include more info on failures, and use ephemeral ports if necessary [\#144](https://github.com/smarr/SOMns/pull/144)

  - Fix various single stepping issues [\#143](https://github.com/smarr/SOMns/pull/143)

  - Fix `#perform:withArguments:` primitive [\#130](https://github.com/smarr/SOMns/pull/130)

  - Make sure that `./som` without arguments does something useful [\#156](https://github.com/smarr/SOMns/issues/156)

## [0.3.0] - [2017-04-06 &lt;Programming&gt;'17 Demo][v3]

### Features for the Demo

 - Added trace replay functionality ([\#109](https://github.com/smarr/SOMns/pull/109))
   - Added `-r` flag to enable replay

 - Visualize all types of activities in system view ([\#116](https://github.com/smarr/SOMns/pull/116))

 - Block methods are named based on outer method's name

 - Enable display of code for unsuspended activities, i.e., activities not
   hitting a breakpoint

### General Maintenance

 - Revised design of promises and implemented erroring/breaking of promises
   ([\#118](https://github.com/smarr/SOMns/pull/118))

 - Updated to Truffle 0.24+patches, from pre-0.22+patches

 - Added `-J` flag for JVM flags, e.g. `-JXmx2g`

 - Removed Truffle Debug REPL support, i.e., the `-td` flag. Has been deprecated
   in Truffle for a long time, and maintaining it seems not useful.

 - Added `-vmd` flag to enable debug output

## [0.2.0] - [2017-03-07 Extended Concurrency Support][v2]

### Concurrency Support

 - Added basic support for shared-memory multithreading and fork/join
   programming ([\#52](https://github.com/smarr/SOMns/pull/52))
   - object model uses now a global safepoint to synchronize layout changes
   - array strategies are not safe yet

 - Added Lee and Vacation benchmarks ([\#78](https://github.com/smarr/SOMns/pull/78))

 - Configuration flag for actor tracing, -atcfg=<config>
   example: -atcfg=mt:mp:pc turns off message timestamps, message parameters and promises

 - Added Validation benchmarks and a new Harness.

 - Added basic Communicating Sequential Processes support.
   See [\#84](https://github.com/smarr/SOMns/pull/88).

 - Added CSP version of PingPong benchmark.

 - Added simple STM implementation. See `s.i.t.Transactions` and [\#81](https://github.com/smarr/SOMns/pull/81) for details.

 - Added breakpoints for channel operations in [\#99](https://github.com/smarr/SOMns/pull/81).

 - Fixed isolation issue for actors. The test that an actor is only created
   from a value was broken ([\#101](https://github.com/smarr/SOMns/issues/101), [\#102](https://github.com/smarr/SOMns/pull/102))

 - Optimize processing of common single messages by avoiding allocation and
   use of object buffer ([\#90](https://github.com/smarr/SOMns/pull/90))

### Interpreter Improvements

 - Turn writes to method arguments into errors. Before it was leading to
   confusing setter sends and 'message not understood' errors.

 - Simplified AST inlining and use objects to represent variable info to improve
   details displayed in debugger ([\#80](https://github.com/smarr/SOMns/pull/80)).

 - Make instrumentation more robust by defining number of arguments of an
   operation explicitly.

 - Add parse-time specialization of primitives. This enables very early
   knowledge about the program, which might be unreliable, but should be good
   enough for tooling. (See [\#75](https://github.com/smarr/SOMns/issues/75) and [\#88](https://github.com/smarr/SOMns/pull/88))

 - Added option to show methods after parsing in IGV with
   `-im`/`--igv-parsed-methods` ([\#110](https://github.com/smarr/SOMns/pull/110))

## [0.1.0] - [2016-12-15][v1]

This is the first tagged version. For previous changes, please refer to the
[pull requests][OldPRs] from around that time.

[v7]: https://github.com/smarr/SOMns/milestone/8?closed=1
[v6]: https://github.com/smarr/SOMns/milestone/7?closed=1
[v5]: https://github.com/smarr/SOMns/milestone/6?closed=1
[v4]: https://github.com/smarr/SOMns/milestone/5?closed=1
[v3]: https://github.com/smarr/SOMns/milestone/3?closed=1
[v2]: https://github.com/smarr/SOMns/milestone/2?closed=1
[v1]: https://github.com/smarr/SOMns/milestone/1?closed=1
[Unreleased]: https://github.com/smarr/SOMns/compare/v0.6.0...HEAD
[0.6.0]:      https://github.com/smarr/SOMns/compare/v0.5.0...v0.6.0
[0.5.0]:      https://github.com/smarr/SOMns/compare/v0.4.0...v0.5.0
[0.4.0]:      https://github.com/smarr/SOMns/compare/v0.3.0...v0.4.0
[0.3.0]:      https://github.com/smarr/SOMns/compare/v0.2.0...v0.3.0
[0.2.0]:      https://github.com/smarr/SOMns/compare/v0.1.0...v0.2.0
[0.1.0]:      https://github.com/smarr/SOMns/releases/tag/v0.1.0
[OldPRs]:    https://github.com/smarr/SOMns/pulls?utf8=%E2%9C%93&q=is%3Apr%20is%3Aclosed%20created%3A2010-01-01..2016-12-15%20
[bd]:        https://github.com/SOM-st/black-diamonds/#readme
[docs]:      https://somns.readthedocs.io/
