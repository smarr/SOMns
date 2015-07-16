#!/bin/bash
echo "Executing Graal with Development Options"

BASE_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
if [ -z "$GRAAL_HOME" ]; then
  if [ -d "$BASE_DIR/../graal" ]; then
    GRAAL_HOME="$BASE_DIR/../graal"
  elif [ -d "$BASE_DIR/../GraalVM" ]; then
    GRAAL_HOME="$BASE_DIR/../GraalVM"
  elif [ -d '/home/smarr/Projects/SOM/graal' ]; then
    GRAAL_HOME='/home/smarr/Projects/SOM/graal'
  elif [ -d '/Users/smarr/Projects/PostDoc/Truffle/graal' ]; then
    GRAAL_HOME='/Users/smarr/Projects/PostDoc/Truffle/graal'
  else
    echo "Please set GRAAL_HOME, could not be found automatically."
    exit 1
  fi
fi

STD_FLAGS="-G:-TruffleBackgroundCompilation \
           -G:+TruffleCompilationExceptionsAreFatal \
           -G:+TraceTrufflePerformanceWarnings \
           -G:+TraceTruffleCompilation -G:+TraceTruffleCompilationDetails\
           -G:+TraceTruffleExpansionSource "

if [ -z "$GRAAL_FLAGS" ]; then
  GRAAL_FLAGS="$STD_FLAGS"
fi

if [ ! -z "$DBG" ]; then
  # GRAAL_DEBUG_SWITCH='-d'
  GRAAL_DEBUG_SWITCH="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000"
fi

if [ ! -z "$IGV" ]; then
  GRAAL_FLAGS="$GRAAL_FLAGS -G:Dump=Truffle,TruffleTree "
fi

## Normally, we should run with asserts enabled
if [ ! -z "$NO_ASSERT" ]; then
  ASSERT="-esa -ea "
fi

## Low Level options
if [ ! -z "$LOW" ]; then
  GRAAL_FLAGS="$GRAAL_FLAGS -XX:+UnlockDiagnosticVMOptions \
               -XX:+LogCompilation -XX:+TraceDeoptimization "
fi

# Splitting
# GRAAL_FLAGS=' -G:+TruffleSplitting '
# GRAAL_FLAGS=' -G:+TruffleSplittingNew 


GRAAL_FLAGS="$GRAAL_FLAGS -G:TruffleCompileOnly=RichardsBenchmarks>>#findTask:"
# moveTopDiskFrom:to:"
#Random>>#next,Bounce>>#benchmark,Ball>>#initialize


#GRAAL_FLAGS="$GRAAL_FLAGS -XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=print,*::callRoot "

#GRAAL_FLAGS="$GRAAL_FLAGS -G:TruffleGraphMaxNodes=1500000 -G:TruffleInliningMaxCallerSize=10000 -G:TruffleInliningMaxCalleeSize=10000 -G:TruffleInliningTrivialSize=10000 -G:TruffleSplittingMaxCalleeSize=100000"

# GRAAL="$GRAAL_HOME/mxtool/mx"
GRAAL="$GRAAL_HOME/jdk1.8.0_45/product/bin/java -server -d64 "

exec $GRAAL $GRAAL_DEBUG_SWITCH $GRAAL_FLAGS $ASSERT \
   -Xbootclasspath/a:build/classes:libs/truffle/build/truffle-api.jar \
   som.VM --platform core-lib/Platform.som "$@"
