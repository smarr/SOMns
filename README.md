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
implementation. Please see the [main project page][SOM] for links to the
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

 [SOM]: http://www.hpi.uni-potsdam.de/hirschfeld/projects/som/