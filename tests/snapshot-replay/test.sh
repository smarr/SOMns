#!/bin/bash
# quit on first error
set -e
iterations=100

if [ "$1" = "1" ]
then
  declare -a Savina=(
  #"PingPong $iterations 0 40000"
    "Counting $iterations 0 50000"
    "ForkJoinThroughput $iterations 0 300:60"
    "ForkJoinActorCreation $iterations 0 4000"
    "ThreadRing $iterations 0 100:10000"
    "Chameneos $iterations 0 100:10000"
    "BigContention $iterations 0 20:12"
    "ConcurrentDictionary $iterations 0 5:100:5"
    "ConcurrentSortedLinkedList $iterations 0 10:1500:10:1"
    "ProducerConsumerBoundedBuffer $iterations 0 40:10:10:60"
    "Philosophers $iterations 0 20:5000"

  )
else
  declare -a Savina=(
    "SleepingBarber $iterations 0 2500:1000:1000:1000"
    "CigaretteSmokers $iterations 0 10000:200"
    "LogisticsMapSeries $iterations 0 25000:10:346"
    "BankTransaction $iterations 0 500:10000"
    "RadixSort $iterations 0 50000:65536:74755"
    "UnbalancedCobwebbedTree $iterations 0 100000:10:500:100"
    "TrapezoidalApproximation $iterations 0 100:1000000:1:5"
    "AStarSearch $iterations 0 100:20"
    "NQueens $iterations 0 20:10:4"
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
      $SOM_DIR/som -EG -as -at -JXmx3000m -JXss8192k core-lib/Benchmarks/AsyncHarness.ns SavinaSnap.$args
      echo ""
      #echo "Replay:"
      #$SOM_DIR/som -EG -as -r -JXmx2000m -JXss8192k -vmd core-lib/Benchmarks/AsyncHarness.ns SavinaSnap.$args
      #echo ""
      echo "========================================================"
      echo ""
      ((counter++))
  done
done
