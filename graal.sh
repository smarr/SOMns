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
  
  GRAAL_FLAGS="-G:-TraceTruffleInlining -G:-TraceTruffleCompilation \
    -G:+TruffleSplittingNew -G:+TruffleCompilationExceptionsAreFatal \
    -G:TruffleInliningMaxCallerSize=10000 "
  if [ "$GRAAL_HOME" = "/Users/smarr/Projects/PostDoc/Truffle/graal" ]; then
    echo Using Graal Development Flags
    GRAAL_FLAGS='-ea -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation
      -G:+TraceTruffleExpansionSource
      -XX:+TraceDeoptimization
      -G:-TruffleBackgroundCompilation
      -G:+TraceTruffleCompilationDetails'
  fi
fi

if [ ! -z "$DBG" ]; then
  GRAAL_DEBUG_SWITCH='-d'
fi

$GRAAL_HOME/mxtool/mx $GRAAL_DEBUG_SWITCH --vm server vm $GRAAL_FLAGS \
   -Xbootclasspath/a:build/classes:libs/truffle.jar \
   som.VM --platform core-lib/Platform.som "$@"
