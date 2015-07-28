#!/bin/sh
java -server -ea -esa -cp build/classes:libs/truffle/build/truffle-api.jar \
        som.VM --platform core-lib/Platform.som \
        "$@"
