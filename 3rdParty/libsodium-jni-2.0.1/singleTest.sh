#!/bin/bash -ev

echo "running single test to find stacktrace if track down JNI loading error"
mvn --quiet clean test -Dtest=RandomTest#testProducesDifferentDefaultRandomBytes
#mvn clean test -Dtest=RandomTest#testProducesDifferentDefaultRandomBytes -X
