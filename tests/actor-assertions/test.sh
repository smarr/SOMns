#!/bin/bash

declare -a Pass=(
    	"testIsMessage"
	"testIsSender"
	"testGetMessage"
	"testGetMessageArgs"
	"testIsMessage2"
	"testIsSender2"
	"testGetMessage2"
	"testIsPromiseMsg2"	
	"testGetSender"
	"testIsPromiseMsg"
	"testIsPromiseComplete"
	"testAssertResultUsed"
	"testAssertResultUsed2"
	"testAssert1"
	"testAssert2"
	"testAssertNext"
	"testAssertNext2"
	"testAssertFuture"
	"testAssertFuture2"
	"testAssertGlobally"
	"testAssertGlobally2"
	"testAssertUntil"
	"testAssertUntil2"
	"testAssertRelease"
	"testAssertRelease2"
) 

declare -a PassEA=(
	"testIsMessage"
	"testIsSender"
	"testGetMessage"
	"testGetMessageArgs"
	"testGetSender"
	"testIsPromiseMsg"
	"testIsPromiseComplete"
	"testAssertResultUsed"
	"testAssert1"
	"testAssertNext"
	"testAssertFuture"
	"testAssertGlobally"
	"testAssertUntil"
	"testAssertRelease"
)

declare -a FailEA=(
	"testAssert2"
	"testAssertNext2"
	"testAssertResultUsed2"
	"testAssertRelease2"
	"testAssertUntil2"
	"testAssertFuture2"
	"testAssertGlobally2"
)



## Determine absolute path of script
pushd `dirname $0` > /dev/null
SCRIPT_PATH=`pwd`
popd > /dev/null

SOM_DIR=$SCRIPT_PATH/../..


echo "No Assertion:" 
for args in "${Pass[@]}"   
do  
  echo "$args" 
  $SOM_DIR/som -G -JXmx1500m -vmd -at core-lib/TestSuite/ActorAssertionTests.som $args 
  if [ $? -ne 0 ]
  then
    echo "Test $args Failed with disabled assertions"
    exit 1
  fi
  
  echo "" 
  echo "========================================================" 
  echo "" 
done 

echo "Assertion:" 
for args in "${PassEA[@]}"   
do  
  echo "$args" 
  $SOM_DIR/som -G -ea -JXmx1500m -vmd -dnu -at core-lib/TestSuite/ActorAssertionTests.som $args 
  if [ $? -ne 0 ]
  then
    echo "Test $args Failed with enabled assertions"
    exit 1
  fi
  
  echo "" 
  echo "========================================================" 
  echo "" 
done 

for args in "${FailEA[@]}"   
do  
  echo "$args" 
  $SOM_DIR/som -G -ea -JXmx1500m -vmd -dnu -at core-lib/TestSuite/ActorAssertionTests.som $args 
  if [ $? -eq 0 ]
  then
    echo "Test $args Failed with enabled assertions"
    exit 1
  fi
  
  echo "" 
  echo "========================================================" 
  echo "" 
done
