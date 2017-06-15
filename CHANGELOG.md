# Change Log

## [Unreleased] - [Concurrency-Agnostic Debugger][v4]

This release introduces concurrency-agnostic debugging based on Kómpos.
It is realized by using a debugger protocol that abstracts from concurrency 
concepts and instead uses a uniform representation and meta data that instructs
Kómpos how to understand and visualize breakpoints, stepping operations, and
data visualization

  - introduced a uniform trace format ([PR #155](https://github.com/smarr/SOMns/pull/155))
  - added process view
  - refactor handling of breakpoints and stepping in interpreter and Kómpos
  - added advanced stepping operations and breakpoints for STM, fork/join, actors, CSP, threads and locks

### Other Enhancements

  - Switch to unified Truffle+Graal repo [PR #149](https://github.com/smarr/SOMns/pull/149) 

  - Updated to Truffle 0.25 [PR #132](https://github.com/smarr/SOMns/pull/132)

  - Use precise array type check [PR #128](https://github.com/smarr/SOMns/pull/128)

  - Make Kómpos tests more robust, include more info in failures, and use ephemeral ports if necessary [PR #144](https://github.com/smarr/SOMns/pull/144)

  - Fix various single stepping issues [PR #143](https://github.com/smarr/SOMns/pull/143)

  - Fix `#perform:withArguments:` primitive [PR #130](https://github.com/smarr/SOMns/pull/130)

  - Make sure that `./som` without arguments does something useful [PR #156](https://github.com/smarr/SOMns/issues/156)
  
## [0.3.0] - [2017-04-06 &lt;Programming&gt;'17 Demo][v3]

### Features for the Demo

 - Added trace replay functionality ([PR #109](https://github.com/smarr/SOMns/pull/109))
   - Added `-r` flag to enable replay

 - Visualize all types of activities in system view ([PR #116](https://github.com/smarr/SOMns/pull/116))

 - Block methods are named based on outer method's name

 - Enable display of code for unsuspended activities, i.e., activities not
   hitting a breakpoint

### General Maintenance

 - Revised design of promises and implemented erroring/breaking of promises
   ([PR #118](https://github.com/smarr/SOMns/pull/118))

 - Updated to Truffle 0.24+patches, from pre-0.22+patches

 - Added `-J` flag for JVM flags, e.g. `-JXmx2g`

 - Removed Truffle Debug REPL support, i.e., the `-td` flag. Has been deprecated
   in Truffle for a long time, and maintaining it seems not useful.

 - Added `-vmd` flag to enable debug output

## [0.2.0] - [2017-03-07 Extended Concurrency Support][v2]

### Concurrency Support

 - Added basic support for shared-memory multithreading and fork/join
   programming ([PR #52](https://github.com/smarr/SOMns/pull/52))
   - object model uses now a global safepoint to synchronize layout changes
   - array strategies are not safe yet

 - Added Lee and Vacation benchmarks ([PR #78](https://github.com/smarr/SOMns/pull/78))

 - Configuration flag for actor tracing, -atcfg=<config>
   example: -atcfg=mt:mp:pc turns off message timestamps, message parameters and promises

 - Added Validation benchmarks and a new Harness.

 - Added basic Communicating Sequential Processes support.
   See [PR #84](https://github.com/smarr/SOMns/pull/88).

 - Added CSP version of PingPong benchmark.

 - Added simple STM implementation. See `s.i.t.Transactions` and [PR #81](https://github.com/smarr/SOMns/pull/81) for details.

 - Added breakpoints for channel operations in [PR #99](https://github.com/smarr/SOMns/pull/81).

 - Fixed isolation issue for actors. The test that an actor is only created
   from a value was broken ([issue #101](https://github.com/smarr/SOMns/issues/101), [PR #102](https://github.com/smarr/SOMns/pull/102))

 - Optimize processing of common single messages by avoiding allocation and
   use of object buffer ([issue #90](https://github.com/smarr/SOMns/pull/90))

### Interpreter Improvements

 - Turn writes to method arguments into errors. Before it was leading to
   confusing setter sends and 'message not understood' errors.

 - Simplified AST inlining and use objects to represent variable info to improve
   details displayed in debugger ([PR #80](https://github.com/smarr/SOMns/pull/80)).

 - Make instrumentation more robust by defining number of arguments of an
   operation explicitly.

 - Add parse-time specialization of primitives. This enables very early
   knowledge about the program, which might be unreliable, but should be good
   enough for tooling. (See [Issue #75](https://github.com/smarr/SOMns/issues/75) and [PR #88](https://github.com/smarr/SOMns/pull/88))

 - Added option to show methods after parsing in IGV with
   `-im`/`--igv-parsed-methods` ([issue #110](https://github.com/smarr/SOMns/pull/110))

## [0.1.0] - [2016-12-15][v1]

This is the first tagged version. For previous changes, please refer to the
[pull requests][OldPRs] from around that time.

[v4]: https://github.com/smarr/SOMns/milestone/5?closed=1
[v3]: https://github.com/smarr/SOMns/milestone/3?closed=1
[v2]: https://github.com/smarr/SOMns/milestone/2?closed=1
[v1]: https://github.com/smarr/SOMns/milestone/1?closed=1
[Unreleased]: https://github.com/smarr/SOMns/compare/v0.3.0...HEAD
[0.3.0]:      https://github.com/smarr/SOMns/compare/v0.2.0...v0.3.0
[0.2.0]:      https://github.com/smarr/SOMns/compare/v0.1.0...v0.2.0
[0.1.0]:      https://github.com/smarr/SOMns/releases/tag/v0.1.0
[OldPRs]:    https://github.com/smarr/SOMns/pulls?utf8=%E2%9C%93&q=is%3Apr%20is%3Aclosed%20created%3A2010-01-01..2016-12-15%20
