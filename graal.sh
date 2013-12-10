#!/bin/bash
BASE_DIR=`pwd`
if [ -z "$GRAAL_HOME" ]; then
  GRAAL_HOME='/home/smarr/Projects/SOM/graal'
  if [ ! -d "$GRAAL_HOME" ]
  then
    GRAAL_HOME='/Users/smarr/Projects/PostDoc/Truffle/graal'
  fi
fi

cd $GRAAL_HOME

./mx.sh --vm server vm -Xbootclasspath/a:$BASE_DIR/build/classes:$BASE_DIR/libs/com.oracle.truffle.api.jar:$BASE_DIR/libs/com.oracle.truffle.api.dsl.jar som.vm.Universe "$@"
