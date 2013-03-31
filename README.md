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

This repository contains a plain Java implementation of SOM, including an implementation of the SOM standard library. Please see the [main project page][SOM] for links to the VM implementation.


SOM can be build with Ant:

    $ ant jar

Afterwards, the tests can be exectued with:

    java -cp build/som.jar som.vm.Universe -cp Smalltalk TestSuite/TestHarness.som
   
A simple Hello World program is executed with:

    java -cp build/som.jar som.vm.Universe -cp Smalltalk Examples/Hello/Hello.som



Information on previous authors are included in the AUTHORS file. This code is
distributed under the MIT License. Please see the LICENSE file for details.

 [SOM]: http://www.hpi.uni-potsdam.de/hirschfeld/projects/som/
