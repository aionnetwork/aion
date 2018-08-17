#!/bin/bash

PACK_PATH='pack'

cd $PACK_PATH
BIN_ARCHIVE="aion.tar.bz2"
tar xvjf $BIN_ARCHIVE
VERSION=$(./aion/aion.sh --version)

BUILD_ARGS="--build-arg KERNEL_PATH=$PACK_PATH/aion/"

docker build -t aion-core:$VERSION ../. $BUILD_ARGS

rm -rf aion/

cd ..


