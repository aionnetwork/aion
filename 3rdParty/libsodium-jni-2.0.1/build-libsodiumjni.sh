#!/bin/bash -ev

. ./setenv.sh

ndk-build
rm -rf src/main/jniLibs/
cp -R libs src/main/jniLibs
gradle build --quiet --full-stacktrace
