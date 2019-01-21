#!/bin/bash
# quit on first error
set -e

if [ "$1" = "1" ]
then
  declare -a Savina=(
    "PingPong 10 0 40000"
    "Counting 10 0 50000"
    "ForkJoinThroughput 10 0 300:60"
    "ForkJoinActorCreation 10 0 4000"
    "ThreadRing 10 0 100:10000"
    "Chameneos 10 0 100:10000"
    "BigContention 10 0 20:12"
    "ConcurrentDictionary 10 0 5:100:5"
    "ConcurrentSortedLinkedList 10 0 10:1500:10:1"
    "ProducerConsumerBoundedBuffer 10 0 40:10:10:60"
    "Philosophers 10 0 20:5000"

  )
else
  declare -a Savina=(
    "SleepingBarber 1 0 2500:1000:1000:1000"
    "CigaretteSmokers 1 0 10000:200"
    "LogisticsMapSeries 1 0 25000:10:346"
    "BankTransaction 1 0 500:10000"
    "RadixSort 1 0 50000:65536:74755"
    "UnbalancedCobwebbedTree 1 0 100000:10:500:100"
    "TrapezoidalApproximation 1 0 100:1000000:1:5"
    "AStarSearch 1 0 100:20"
    "NQueens 1 0 20:10:4"
  )
fi

## Determine absolute path of script
pushd `dirname $0` > /dev/null
SCRIPT_PATH=`pwd`
popd > /dev/null

SOM_DIR=$SCRIPT_PATH/../..

for args in "${Savina[@]}"
do
  counter=1
  while [ $counter -le 1 ]
  do

      echo "$counter. $args"
      echo "Tracing:"
      $SOM_DIR/som -EG -as -at -JXmx1500m -JXss8192k core-lib/Benchmarks/AsyncHarness.ns SavinaSnap.$args
      echo ""
      echo "Replay:"
      $SOM_DIR/som -EG -as -r -JXmx1500m -JXss8192k -vmd core-lib/Benchmarks/AsyncHarness.ns SavinaSnap.$args
      echo ""
      echo "========================================================"
      echo ""
      ((counter++))
  done
done
