#!/bin/bash
## Script fast execution, fewest possible debug options

BASE_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
if [ -z "$GRAAL_HOME" ]; then
  if [ -d "$BASE_DIR/../graal" ]; then
    GRAAL_HOME="$BASE_DIR/../graal"
  elif [ -d "$BASE_DIR/../GraalVM" ]; then
    GRAAL_HOME="$BASE_DIR/../GraalVM"
  elif [ -d '/home/smarr/Projects/SOM/graal']; then
    GRAAL_HOME='/home/smarr/Projects/SOM/graal'
  elif [ -d '/Users/smarr/Projects/PostDoc/Truffle/graal' ]; then
    GRAAL_HOME='/Users/smarr/Projects/PostDoc/Truffle/graal'
  else
    echo "Please set GRAAL_HOME, could not be found automatically."
    exit 1
  fi
fi

STD_FLAGS="-G:-TraceTruffleInlining \
           -G:-TraceTruffleCompilation \
           -G:+TruffleCompilationExceptionsAreFatal "
#-G:+TruffleSplitting 

if [ -z "$GRAAL_FLAGS" ]; then
  GRAAL_FLAGS="$STD_FLAGS "
fi

if [ ! -z "$DBG" ]; then
  GRAAL_DEBUG_SWITCH='-d'
fi

if [ ! -z "ASSERT" ]; then
  USE_ASSERT="-esa -ea "
fi

exec $GRAAL_HOME/mxtool/mx $GRAAL_DEBUG_SWITCH --vm server vm $GRAAL_FLAGS $GF \
   -Xss160M $USE_ASSERT \
   -Xbootclasspath/a:build/classes:libs/truffle.jar \
   som.VM --platform core-lib/Platform.som "$@"
