#!/bin/sh
java -cp build/classes:libs/com.oracle.truffle.api.jar \
		som.vm.Universe \
		"$@"
