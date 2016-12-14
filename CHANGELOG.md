# Change Log


## [Unreleased]

 - Added basic support for shared-memory multithreading and fork/join
   programming ([PR #52](https://github.com/smarr/SOMns/pull/52))
   - object model uses now a global safepoint to synchronize layout changes
   - array strategies are not safe yet
 - Turn writes to method arguments into errors. Before it was leading to 
   confusing setter sends and 'message not understood' errors.
 - Added Lee and Vacation benchmarks ([PR #78](https://github.com/smarr/SOMns/pull/78))

 - Configuration flag for actor tracing, -atcfg=<config>
   example: -atcfg=mt:mp:pc turns off message timestamps, message parameters and promises

## 0.1.0 - 2016-12-15

This is the first tagged version. For previous changes, please refer to the
[pull requests][OldPRs] from around that time.


[Unreleased]: https://github.com/smarr/SOMns-vscode/compare/v0.1.0...HEAD
[OldPRs]:    https://github.com/smarr/SOMns/pulls?utf8=%E2%9C%93&q=is%3Apr%20is%3Aclosed%20created%3A2010-01-01..2016-12-15%20
