#!/bin/bash
set -e # make script fail on first error

# make SCRIPT_PATH absolute
pushd `dirname $0` > /dev/null
SCRIPT_PATH=`pwd`
popd > /dev/null

source ${SCRIPT_PATH}/config.inc

function check_for() {
  if [ ! -x `which $1` ]
  then
    ERR "$1 binary not found. $2"
    if [ "non-fatal" -ne "$3" ]
    then
      exit 1
    fi
  fi
}

## Make sure we have all dependencies
check_for "packer" "Packer does not seem available. We tested with packer version 1.4 from https://www.packer.io/downloads.html"

echo Compose a VirtualBox Image
pushd ${SCRIPT_PATH}

packer build -force packer.json

echo File Size
ls -sh ~/artifacts/${BOX_NAME}/*

echo MD5
md5sum ~/artifacts/${BOX_NAME}/*

chmod a+r ~/artifacts/${BOX_NAME}/*
