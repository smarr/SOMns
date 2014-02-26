#!/bin/bash
BASE_DIR=`pwd`
if [ -z "$GRAAL_HOME" ]; then
  GRAAL_HOME='/home/smarr/Projects/SOM/graal'
  if [ ! -d "$GRAAL_HOME" ]
  then
    GRAAL_HOME='/Users/smarr/Projects/PostDoc/Truffle/graal'
  fi
fi

if [ -z "$GRAAL_FLAGS" ]; then
  GRAAL_FLAGS='-G:-TraceTruffleInlining -G:-TraceTruffleCompilation'
  
  if [ "$GRAAL_HOME" = "/Users/smarr/Projects/PostDoc/Truffle/graal" ]; then
    echo Using Graal Development Flags
    GRAAL_FLAGS='-ea -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation
      -G:+TraceTruffleExpansion -G:+TraceTruffleExpansionSource
      -XX:+TraceDeoptimization
      -G:-TruffleBackgroundCompilation
      -G:+TraceTruffleCompilationDetails'
  fi
fi
$GRAAL_HOME/mxtool/mx --vm server vm $GRAAL_FLAGS \
   -Xbootclasspath/a:build/classes:libs/truffle.jar \
   som.vm.Universe "$@"
