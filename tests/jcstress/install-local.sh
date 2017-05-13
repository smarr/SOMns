#!/bin/sh
mvn install:install-file \
  -Dfile=/Users/smarr/Projects/SOM/SOMns/libs/truffle/truffle/mxbuild/dists/truffle-api.jar \
  -DgroupId=truffle-api \
  -DartifactId=truffle-api -Dversion=0.0.1 \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=/Users/smarr/Projects/SOM/SOMns/libs/truffle/truffle/mxbuild/dists/truffle-debug.jar \
  -DgroupId=truffle-debug \
  -DartifactId=truffle-debug -Dversion=0.0.1 \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=/Users/smarr/Projects/SOM/SOMns/libs/somns-deps.jar \
  -DgroupId=somns-deps \
  -DartifactId=somns-deps -Dversion=0.0.1 \
  -Dpackaging=jar

mvn install:install-file \
  -Dfile=/Users/smarr/Projects/SOM/SOMns/build/som.jar \
  -DgroupId=som \
  -DartifactId=som -Dversion=0.0.1 \
  -Dpackaging=jar
