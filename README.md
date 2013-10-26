SOM - Simple Object Machine
===========================

SOM is a minimal Smalltalk dialect used to teach VM construction at the [Hasso
Plattner Institute][SOM]. It was originally built at the University of Århus
(Denmark) where it was also used for teaching.

Currently, implementations exist for Java (SOM), C (CSOM), C++ (SOM++), and
Squeak/Pharo Smalltalk (AweSOM).

A simple SOM Hello World looks like:

```Smalltalk
Hello = (
  run = (
    'Hello World!' println.
  )
)
```

This repository contains the standard library of SOM, without an actual SOM
implementation. Please see the [main project page][SOMst] for links to the
VM implementation.

With CSOM, the given example could be executed for instance like:
   `./CSOM -cp Smalltalk Hello.som`

AweSOM can be asked to directly evaluate a given string, for instance like:
   `SOMUniverse new eval: '''Hello World!'' println'.'.`

A version of AweSOM is available for Pharo via:
```Smalltalk
Gofer it
    url: 'http://ss3.gemstone.com/ss/AweSOM';
    package: 'ConfigurationOfAweSOM';
    load.
(Smalltalk at: #ConfigurationOfAweSOM) loadDevelopment
```

To install it into a recent Squeak, use the following expression:
```Smalltalk
Installer ss3
    project: 'AweSOM';
    install: 'ConfigurationOfAweSOM'.
(Smalltalk at: #ConfigurationOfAweSOM) perform: #loadDevelopment
```

Information on previous authors are included in the AUTHORS file. This code is
distributed under the MIT License. Please see the LICENSE file for details.


Build Status
------------

Thanks to Travis CI, all commits of this repository are tested.
The current build status is: [![Build Status](https://travis-ci.org/SOM-st/SOM.png?branch=master)](https://travis-ci.org/SOM-st/SOM/)

The build status of the SOM implementations is as follows:

* CSOM: [![CSOM Build Status](https://travis-ci.org/SOM-st/CSOM.png?branch=master)](https://travis-ci.org/SOM-st/CSOM/)
* SOM (Java): [![SOM Java Build Status](https://travis-ci.org/SOM-st/som-java.png?branch=master)](https://travis-ci.org/SOM-st/som-java/)
* PySOM: [![PySOM Build Status](https://travis-ci.org/SOM-st/PySOM.png?branch=master)](https://travis-ci.org/SOM-st/PySOM)
* RPySOM: [![RPySOM Build Status](https://travis-ci.org/SOM-st/RPySOM.png?branch=master)](https://travis-ci.org/SOM-st/RPySOM)
* TruffleSOM: [![TruffleSOM Build Status](https://travis-ci.org/SOM-st/TruffleSOM.png?branch=master)](https://travis-ci.org/SOM-st/TruffleSOM)


 [SOM]: http://www.hpi.uni-potsdam.de/hirschfeld/projects/som/
 [SOMst]: https://travis-ci.org/SOM-st/
