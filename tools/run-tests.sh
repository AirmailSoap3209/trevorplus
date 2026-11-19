#!/bin/sh
# Runs the core unit tests (pure Java, no Minecraft). Run from the project root: sh tools/run-tests.sh
set -e
rm -rf build-tmp; mkdir -p build-tmp/out build-tmp/out-test
javac -d build-tmp/out $(find src/main -name '*.java' ! -name 'Trevorplus.java')
javac -cp build-tmp/out -d build-tmp/out-test tools/tests/com/trevorplus/TestMain.java
java -cp build-tmp/out:build-tmp/out-test com.trevorplus.TestMain
rm -rf build-tmp
