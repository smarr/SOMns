# Infrastructure

This gives a brief overview of some of the infrastructure used in SOMns.

## Build System

SOMns uses Ant as build system. The setup tries to minimize
the external software dependencies. Currently, instead of using some automatic
dependency management system for SOMns, we use an *uberjar* that combines all
rarely changing Java dependencies.

The corresponding project is [SOMns-deps](https://github.com/smarr/SOMns-deps),
which is essentially a shell script creating a *jar* file from a set of
libraries an then uploading it onto [Bintray](https://bintray.com/smarr/SOM).

The Truffle library is however directly used as a
[git submodule](https://git-scm.com/book/en/v2/Git-Tools-Submodules) dependency,
because it changes frequently, and we sometimes need changes in Truffle.
Currently, SOMns also relies on a personal fork of Truffle to support changes
in the instrumentation and debugging support.

## GitHub

SOMns relies on GitHub, its issue tracking, and pull request system for
development.

**Change Tracking with Pull Requests:**  The general approach is all changes are
tracked with a pull request.

When you are getting started with working on the SOMns interpreter internals,
consider checking out the
[**Good First Issue**](https://github.com/smarr/SOMns/labels/good%20first%20issue)
label. These issues are more or less simple changes that with a bit of guidance
should provide a good introduction to the SOMns code base, an basic
understanding of how Truffle-based interpreters work, and a few SOMns specific
insights.

## Code Style

When working on SOMns code, please look at the code around you and stick to the
style. It might be *particular*, but it is consistent in this code base.

To ensure basic compliance with the style, we **use
[checkstyle](http://checkstyle.sourceforge.net/)**. It is integrated into the
build system and continuous integration system. Please use something like
[Eclipse Checkstyle](http://eclipse-cs.sourceforge.net/) to integrate it in
your editor.

We are also using Codacy to monitor additional style issues or potential bugs.
See the [STM pull request](https://github.com/smarr/SOMns/pull/81#pullrequestreview-17422244) for examples.

## Development Support

**Continuous Integration:** To automatically run unit tests for the interpreter, SOMns, and the debugger,
we use [Travis CI](https://travis-ci.org/smarr/SOMns/builds) (see `.travis.yml`)
as well as a private GitLab instance to run benchmarks (see `.gitlab-ci.yml`).

In case you forked SOMns on GitHub for your own work, please consider [enabling Travis CI](https://docs.travis-ci.com/user/getting-started/).

The current build status is: [![Build Status](https://travis-ci.org/smarr/SOMns.png?branch=master)](https://travis-ci.org/smarr/SOMns)

**Performance Tracking:**
Since one goal of SOMns is to be a platform for research on concurrency with
performance close to state-of-the-art JVMs, we continuously track benchmark
performance, for startup as well as peak performance with
[Codespeed](http://somns-speed.stefan-marr.de/timeline/#/?exe=14,23,19,21,22,20,16,17,15,18&base=none&ben=peak.Havlak&env=1&revs=10&equid=off).
It is run on every change to the master branch, and can be used to track and
compare performance of experimental changes as well.

<figure style="text-align:center">
<img style="width:400px" src="../codespeed.png" alt="SOMns Codespeed: Havlak performance" />
<figcaption>
SOMns Codespeed, tracking benchmark performance. Example shows *Havlak* peak
performance.
</figcaption>
</figure>

The benchmark execution is configured with `codespeed.conf` and are executed
with the [ReBench](https://github.com/smarr/ReBench) tool.

**SOMns Code Coverage:**
To track SOMns code coverage, we use
[Coveralls](https://coveralls.io/github/smarr/SOMns).