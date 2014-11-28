#!/bin/bash
BASE_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
if [ -z "$GRAAL_HOME" ]; then
  GRAAL_HOME="$BASE_DIR/../graal"
  if [ ! -d "$GRAAL_HOME" ]
  then
    GRAAL_HOME="$BASE_DIR/../GraalVM"
    if [ ! -d "$GRAAL_HOME" ]
    then
      echo "Please set GRAAL_HOME, could not be found automatically."
      exit 1
    fi
  fi
fi

if [ -z "$GRAAL_FLAGS" ]; then
  GRAAL_FLAGS='-G:-TraceTruffleInlining -G:-TraceTruffleCompilation -G:+TruffleSplitting -G:+TruffleCompilationExceptionsAreFatal'
fi

if [ ! -z "$DBG" ]; then
  GRAAL_DEBUG_SWITCH='-d'
fi

#GRAAL_FLAGS='-ea -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation
#      -G:+TraceTruffleExpansion -G:+TraceTruffleExpansionSource
#      -XX:+TraceDeoptimization
#      -G:-TruffleBackgroundCompilation
#      -G:+TraceTruffleCompilationDetails'

#GRAAL_FLAGS="$GRAAL_FLAGS -G:TruffleCompileOnly=Ball>>#initialize "  #Random>>#next,Bounce>>#benchmark

#GRAAL_FLAGS="$GRAAL_FLAGS -G:Dump=Truffle,TruffleTree "
#GRAAL_FLAGS="$GRAAL_FLAGS -G:+TraceTruffleCompilation "
#GRAAL_FLAGS="$GRAAL_FLAGS -XX:+UnlockDiagnosticVMOptions -XX:CompileCommand=print,*::callRoot "

#GRAAL_FLAGS="$GRAAL_FLAGS -G:TruffleGraphMaxNodes=1500000 -G:TruffleInliningMaxCallerSize=10000 -G:TruffleInliningMaxCalleeSize=10000 -G:TruffleInliningTrivialSize=10000 -G:TruffleSplittingMaxCalleeSize=100000"

ASSERT="-esa -ea "
$GRAAL_HOME/mxtool/mx $GRAAL_DEBUG_SWITCH --vm server vm $GRAAL_FLAGS $GF -Xss160M $ASSERT \
   -Xbootclasspath/a:build/classes:libs/truffle.jar \
   som.vm.Universe "$@"
