#!/usr/bin/env make -f

all: compile

clean:
	ant clean

compile: build/som.jar

build/som.jar:
	ant jar

test:
	ant test

upload-tools:
	scp tools/*.html tools/*.json ts:www-truffle/dynamic-metrics/
