#!/bin/bash -ev

. ./setenv.sh

sudo cp ./libsodium/libsodium-host/lib/libsodium.so /usr/local/lib

pushd jni
./compile.sh
popd

mvn -q clean install
./singleTest.sh
