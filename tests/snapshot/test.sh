#!/bin/bash
# quit on first error
set -e
  declare -a Savina=(
    "PingPong 1 0 40000"
    "Counting 1 0 200000"
    "ForkJoinThroughput 1 0 3000:60"
    #"ForkJoinActorCreation 1 0 20000"
    "ThreadRing 1 0 100:50000"
    "Chameneos 1 0 100:20000"
    "BigContention 1 0 500:120"
    "ConcurrentDictionary 1 0 20:600:20"
    "ConcurrentSortedLinkedList 1 0 10:500:10:1"
    "ProducerConsumerBoundedBuffer 1 0 40:5:5:10"
    "Philosophers 1 0 20:2000"
    "SleepingBarber 1 0 800:500:500:200"
    "CigaretteSmokers 1 0 1000:200"
    "LogisticsMapSeries 1 0 10000:10:346"
    "BankTransaction 1 0 1000:10000"
    "RadixSort 1 0 10000:65536:74755"
    "UnbalancedCobwebbedTree 1 0 10000:10:0:1"
    "TrapezoidalApproximation 1 0 100:100000:1:5"
    "AStarSearch 1 0 100:10"
    "NQueens 1 0 20:8:4"
  )


## Determine absolute path of script
pushd `dirname $0` > /dev/null
SCRIPT_PATH=`pwd`
popd > /dev/null

SOM_DIR=$SCRIPT_PATH/../..
#-JXmx4g
for args in "${Savina[@]}"
do
  echo "$args"
  echo "Tracing:"
  $SOM_DIR/som -G -JXss4096k -at -as -sam -TF core-lib/Benchmarks/AsyncHarness.ns Savina.$args
  echo ""
  echo "========================================================"
  echo ""
done
